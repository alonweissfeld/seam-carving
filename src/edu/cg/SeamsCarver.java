package edu.cg;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public class SeamsCarver extends ImageProcessor {
	private enum path {L, V, R};

	/**
	 * Represent a greyscale pixel with it's associated
	 * energy and the parent direction.
	 */
	private static class EnergyPixel {
		private int intensity; // grey value
		private long energy;
		private path parent;

		EnergyPixel(int g, long e) {
			this.intensity = g;
			this.energy = e;
		}

		// Getters and setters for parent and energy
		public int getIntensity() { return this.intensity; }
		public long getEnergy() { return this.energy; }
		public void setEnergy(long e) { this.energy = e; }
		public path getParent() { return this.parent; }
		public void setParent(path p) { this.parent = p; }
	}

	// MARK: An inner interface for functional programming.
	@FunctionalInterface
	interface ResizeOperation {
		BufferedImage resize();
	}

	// MARK: Fields
	private int numOfSeams;
	private ResizeOperation resizeOp;
	boolean[][] imageMask;
	int[][] greyscale;
	EnergyPixel[][] costMatrix;
	int k; // Number of seams that were handled.
	BufferedImage tempImg;

	public SeamsCarver(Logger logger, BufferedImage workingImage, int outWidth, RGBWeights rgbWeights,
			boolean[][] imageMask) {
		super((s) -> logger.log("Seam carving: " + s), workingImage, rgbWeights, outWidth, workingImage.getHeight());

		numOfSeams = Math.abs(outWidth - inWidth);
		this.imageMask = imageMask;
		if (inWidth < 2 | inHeight < 2)
			throw new RuntimeException("Can not apply seam carving: workingImage is too small");

		if (numOfSeams > inWidth / 2)
			throw new RuntimeException("Can not apply seam carving: too many seams...");

		// Setting resizeOp by with the appropriate method reference
		if (outWidth > inWidth)
			resizeOp = this::increaseImageWidth;
		else if (outWidth < inWidth)
			resizeOp = this::reduceImageWidth;
		else
			resizeOp = this::duplicateWorkingImage;

		k = 0; // init number of seams

		this.logger.log("preliminary calculations were ended.");
	}

	public BufferedImage resize() {
		return resizeOp.resize();
	}

	private BufferedImage reduceImageWidth() {
		logger.log("Starting to reduce image...");
		BufferedImage tempImg = newEmptyInputSizedImage();
		BufferedImage result = newEmptyOutputSizedImage();

		// Copy working image
		forEach((y, x) -> tempImg.setRGB(x, y, workingImage.getRGB(x, y)));
		this.tempImg = tempImg;

		// Find all seams...
		int[][] seams = this.findSeams();

		// Trim temp image.
		setForEachOutputParameters();
		forEach((y, x) -> result.setRGB(x, y, tempImg.getRGB(x, y)));
		logger.log("Done reducing image.");
		return result;
	}

	private BufferedImage increaseImageWidth() {
		// TODO: Implement this method, remove the exception.
		throw new UnimplementedMethodException("increaseImageWidth");
	}

	public BufferedImage showSeams(int seamColorRGB) {
		// TODO: Implement this method (bonus), remove the exception.
		throw new UnimplementedMethodException("showSeams");
	}

	public boolean[][] getMaskAfterSeamCarving() {
		boolean[][] mask = new boolean[outHeight][outWidth];
		setForEachOutputParameters();
		forEach((y, x) -> mask[y][x] = imageMask[y][x]);
		return mask;

		// TODO: Implement this method, remove the exception.
		// This method should return the mask of the resize image after seam carving.
		// Meaning, after applying Seam Carving on the input image,
		// getMaskAfterSeamCarving() will return a mask, with the same dimensions as the
		// resized image, where the mask values match the original mask values for the
		// corresponding pixels.
		// HINT: Once you remove (replicate) the chosen seams from the input image, you
		// need to also remove (replicate) the matching entries from the mask as well.
	}

	private path getPathByMinimum(long a, long b, long c) {
		long minVal = Math.min(a, Math.min(b, c));
		if (minVal == a) return path.L;
		if (minVal == b) return path.V;
		return path.R;
	}

	private void setGreyscale() {
		logger.log("Converting to greyscale...");
		int[][] result = new int[inHeight][inWidth];

		forEach((y, x) -> {
			Color c =  new Color(workingImage.getRGB(x, y));
			int greyVal = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
			Color greyCol = new Color(greyVal, greyVal, greyVal);
			result[y][x] = greyCol.getRGB();
		});

		this.greyscale = result;
		logger.log("Done converting to greyscale.");
	}

	/**
	 * Calculate the gradient magnitude matrix.
	 */
	private void initCostMatrix(int height, int width) {
		logger.log("Initiating cost matrix...");
		EnergyPixel[][] E = new EnergyPixel[height][width];

		for (int y = 0; y < height; ++y) {
			for (int x = 0; x < width; ++x) {
				E[y][x] = new EnergyPixel(greyscale[y][x], this.calcEnergy(y, x));
			}
		}

		logger.log("Done initiating cost matrix by pixel energies.");
		this.costMatrix = E;
	}

	private void calcForwardCostMatrix() {
		logger.log("Calculating forward looking cost matrix...");
		forEach(this::calcRecursiveCost);
		logger.log("Done calculating cost matrix.");
	}

	private void updateForwardCostMatrix() {
		setForEachWidth(inWidth - k);
		this.initCostMatrix(inHeight, inWidth - k);
		this.calcForwardCostMatrix();
	}

	private void updateMatrices(int[] seam) {
		logger.log("Updating matrices...");
		// Update gradient to pixels who are left to the seam
		for (int y = 0; y < costMatrix.length; y++) {
			// Shift left all pixels that are right to the seam
			for (int x = seam[y]; x < inWidth - k; x++) {
				imageMask[y][x] = imageMask[y][x + 1];
				greyscale[y][x] = greyscale[y][x + 1];
				tempImg.setRGB(x, y, tempImg.getRGB(x + 1, y));
			}
		}

		this.updateForwardCostMatrix();
		logger.log("Done updating matrices for seam #" + k);
	}

	private long calcEnergy(int y, int x) {
		int neighborX = (x < inWidth - 1 - k) ? x + 1 : x - 1;
		int neighborY = (y < inHeight - 1 - k) ? y + 1 : y - 1;

		long energy;
		if (imageMask[y][x]) {
			return Long.MIN_VALUE;
		}

		int deltaX = greyscale[y][neighborX] - greyscale[y][x];
		int deltaY = greyscale[neighborY][x] - greyscale[y][x];
		return Math.abs(deltaX) + Math.abs(deltaY);
	}

	private int[][] findSeams() {
		logger.log("Searching seams...");
		int [][] seams = new int[numOfSeams][inHeight];

		this.setGreyscale();
		this.initCostMatrix(inHeight, inWidth);
		this.calcForwardCostMatrix();

		for (k = 1; k < numOfSeams; ++k) {
			int[] seam = findSeam();
			seams[k - 1] = seam;

			this.updateMatrices(seam);
		}
		logger.log("Done searching for new seams.");
		return seams;
	}

	private int[] findSeam() {
		logger.log("Finding optimal seam #" + k);
		int[] seam = new int[inHeight];

		// Get the index of the minimum cost from the
		// last row of the cost matrix.
		int j = inHeight - 1;
		int idx = this.findMinCostIdx(this.costMatrix[j]);

		for (int i = seam.length - 1; i >= 0; i--) {
			seam[i] = idx;
			path p = costMatrix[j][idx].getParent();
			if (p == path.L) { idx -= 1; }
			else if (p == path.R) { idx += 1; }
			j--;
		}

		return seam;
	}

	private int findMinCostIdx(EnergyPixel[] arr) {
		int minIdx = 0;
		long min = arr[0].getEnergy();

		for (int i = 1; i < arr.length; i++) {
			if (arr[i].getEnergy() < min) {
				min = arr[i].getEnergy();
				minIdx = i;
			}
		}

		return minIdx;
	}

	private void calcRecursiveCost(int y, int x) {
		if (y == 0) {
			return; // Avoid calculating for base case - first row.
		}

		long left = Long.MAX_VALUE;
		long right = Long.MAX_VALUE;

		// All cases
		boolean isBorder = (x == 0 || x == (this.costMatrix[0].length - 1));

		int cv = isBorder ? 0 : Math.abs(greyscale[y][x + 1] - greyscale[y][x - 1]);
		long center = costMatrix[y - 1][x].getEnergy() + cv;

		// Excluding first column
		if (x != 0) {
			int cl = isBorder ? 0 : Math.abs(greyscale[y][x + 1] - greyscale[y][x - 1]);
			cl += Math.abs(greyscale[y - 1][x] - greyscale[y][x - 1]);

			left = costMatrix[y - 1][x - 1].getEnergy() + cl;
		}

		// Excluding last column
		if (x != (this.costMatrix[0].length - 1)) {
			// There is no left edge for the first column.
			int cr = isBorder ? 0 : Math.abs(greyscale[y][x + 1] - greyscale[y][x - 1]);
			cr += Math.abs(greyscale[y][x + 1] - greyscale[y - 1][x]);

			right = costMatrix[y - 1][x + 1].getEnergy() + cr;
		}

		path p = getPathByMinimum(left, center, right);

		long val;
		if (p == path.L) val = left;
		else if (p == path.V) val = center;
		else val = right;

		costMatrix[y][x].setEnergy(costMatrix[y][x].getEnergy() + val);
		costMatrix[y][x].setParent(p);
	}
}
