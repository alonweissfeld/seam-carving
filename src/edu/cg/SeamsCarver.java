package edu.cg;

import java.awt.*;
import java.awt.image.BufferedImage;

public class SeamsCarver extends ImageProcessor {
	private enum path {L, V, R};

	/**
	 * Represent a greyscale pixel with it's associated
	 * energy and the parent direction.
	 */
	private static class EnergyPixel {
		private int intensity; // gery value
		private long energy;
		private path parent;

		EnergyPixel(int g, long e) {
			this.intensity = g;
			this.energy = e;
		}

		// Getters and setters for parent and energy
		public int getIntensity() { return this.intensity; }
		public long getEnergy() { return this.energy; }
		public void setEnergy(long e) { this.energy = e;}
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

		// TODO: You may initialize your additional fields and apply some preliminary
		// calculations.

		this.logger.log("preliminary calculations were ended.");
	}

	public BufferedImage resize() {
		return resizeOp.resize();
	}

	private BufferedImage reduceImageWidth() {
		BufferedImage result = newEmptyOutputSizedImage();

		this.setGreyscale();
		this.initCostMatrix();
		this.calcForwardCostMatrix();

		int[][] seams = this.findSeams();
		// TODO: remove seams
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
		// TODO: Implement this method, remove the exception.
		// This method should return the mask of the resize image after seam carving.
		// Meaning, after applying Seam Carving on the input image,
		// getMaskAfterSeamCarving() will return a mask, with the same dimensions as the
		// resized image, where the mask values match the original mask values for the
		// corresponding pixels.
		// HINT: Once you remove (replicate) the chosen seams from the input image, you
		// need to also remove (replicate) the matching entries from the mask as well.
		throw new UnimplementedMethodException("getMaskAfterSeamCarving");
	}

	private path getPathByMinimum(long a, long b, long c) {
		long minVal = Math.min(a, Math.min(b, c));
		if (minVal == a) return path.L;
		if (minVal == b) return path.V;
		return path.R;
	}

	private void setGreyscale() {
		int[][] result = new int[inHeight][inWidth];

		forEach((y, x) -> {
			Color c =  new Color(workingImage.getRGB(x, y));
			int greyVal = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
			Color greyCol = new Color(greyVal, greyVal, greyVal);
			result[y][x] = greyCol.getRGB();
		});

		this.greyscale = result;
	}

	private void initCostMatrix() {
		// Calculate the gradient magnitude matrix.
		EnergyPixel[][] E = new EnergyPixel[inHeight][inWidth];

		if (this.greyscale == null) {
			this.setGreyscale();
		}

		forEach((y, x) -> E[y][x] = new EnergyPixel(greyscale[y][x], this.calcEnergy(y, x)));
		this.costMatrix = E;
	}

	private void calcForwardCostMatrix() {
		forEach((y, x) -> {
			// Avoid calculating for base case - first row.
			if (y != 0) {
				this.calcRecursiveCost(y, x);
			}
		}); // Done building dynamic programming matrix.
	}

	private void updateCostMatrix(int[] seam) {
		// Update gradient to pixels who are left to the seam
		for (int y = 0; y < costMatrix.length; y++) {
			// Shift left all pixels that are right to the seam

			for (int x = seam[y]; x < inWidth; x++) {
				imageMask[y][x] = imageMask[y][x + 1];
				greyscale[y][x] = greyscale[y][x + 1];
				costMatrix[y][x] = costMatrix[y][x + 1];
			}

			int x = seam[y] - 1;
			costMatrix[y][x].setEnergy(this.calcEnergy(y, x));
		}

		for (int y = 1; y < costMatrix.length; y++) {
			this.calcRecursiveCost(y, seam[y] - 1);
			this.calcRecursiveCost(y, seam[y]);
		}
	}

	private long calcEnergy(int y, int x) {
		int neighborX = (x < inWidth - 2) ? x + 1 : x - 1;
		int neighborY = (y < inHeight - 2) ? y + 1 : y - 1;

		long energy;
		if (imageMask[y][x]) {
			return Long.MIN_VALUE;
		}

		int deltaX = greyscale[y][neighborX] - greyscale[y][x];
		int deltaY = greyscale[neighborY][x] - greyscale[y][x];
		return (long) Math.sqrt((1 << deltaX) + (1 << deltaY));
	}

	private int[][] findSeams() {
		int [][] seams = new int[this.numOfSeams][inHeight];
		for (int k = 0; k < this.numOfSeams; k++) {
			int[] seam = findSeam();
			seams[k] = seam;

			this.updateCostMatrix(seam);
		}
		return seams;
	}

	private int[] findSeam() {
		int[] seam = new int[inHeight];

		// Get the index of the minimum cost from the
		// last row of the cost matrix.
		int j = inHeight - 1;
		int idx = this.findMinCostIdx(this.costMatrix[j]);

		for (int i = seam.length - 1; i >= 0; i--) {
			seam[i] = idx;
			path p = costMatrix[j--][idx].getParent();
			if (p == path.L) { idx -= 1; }
			else if (p == path.R) { idx += 1; }
		}

		return seam;
	}

	private int findMinCostIdx(EnergyPixel[] arr) {
		int minIdx = 0;
		long min = arr[0].getEnergy();

		for (int i = 1; i <arr.length; i++) {
			if (arr[i].getEnergy() < min) {
				min = arr[i].getEnergy();
				minIdx = i;
			}
		}

		return minIdx;
	}

	private void calcRecursiveCost(int y, int x) {
		long left = Long.MAX_VALUE;
		long right = Long.MAX_VALUE;

		// All cases
		long center = costMatrix[y - 1][x].getEnergy()
				+ Math.abs(greyscale[y][x + 1] - greyscale[y][x - 1]);

		// Excluding first column
		if (x != 0) {
			int cl = Math.abs(greyscale[y][x + 1] - greyscale[y][x - 1]);
			cl += Math.abs(greyscale[y - 1][x] - greyscale[y][x - 1]);

			left = costMatrix[y - 1][x - 1].getEnergy() + cl;
		}

		// Excluding last column
		if (x != inWidth - 1) {
			int cr = Math.abs(greyscale[y][x + 1] - greyscale[y][x - 1]);
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
