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
		private long energy;
		private path parent;

		EnergyPixel(long e) {
			this.energy = e;
		}

		// Getters and setters for parent and energy
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
	boolean opReduce;
	BufferedImage tempImg;
	int[][] shiftedSeams;
	int[][] increasedSeams;

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
		opReduce = true;
		forEach((y, x) -> tempImg.setRGB(x, y, workingImage.getRGB(x, y)));
		this.tempImg = tempImg;

		// Find all seams.
		// During the process of finding new seams, we reduce the previous
		// ones and therefore resizing the image accordingly.
		this.findSeams();

		// Trim temp image to desired output size.
		setForEachOutputParameters();
		forEach((y, x) -> result.setRGB(x, y, tempImg.getRGB(x, y)));
		logger.log("Done reducing image.");
		return result;
	}


	private BufferedImage increaseImageWidth() {
		logger.log("Starting to increase image...");
		BufferedImage result = newEmptyOutputSizedImage();
		boolean[][] tempMask = new boolean[outHeight][outWidth];

		// Find all seams.
		this.findSeams();

		// Iterate all pixels in the desired output image.
		// Upon encountering a seam, indent the x's coordinate
		// from which to get the RGB value.
		for (int y = 0; y < outHeight; y++) {
			int indent = 0;
			for (int x = 0; x < outWidth; x++) {
				result.setRGB(x, y, workingImage.getRGB(x - indent, y));
				tempMask[y][x] = imageMask[y][x - indent];

				if (isPartOfSeam(x, y)) indent++;
			}
		}

		imageMask = tempMask;
		logger.log("Done increase image.");
		return result;
	}

	/**
	 * Returns true of a given x coordinate is a part of a seam.
	 */
	private boolean isPartOfSeam(int x, int row) {
		for (int[] seam : increasedSeams) if (seam[row] == x) return true;
		return false;
	}

	public BufferedImage showSeams(int seamColorRGB) {
		BufferedImage result = newEmptyInputSizedImage();
		forEach((y, x) -> result.setRGB(x, y, workingImage.getRGB(x, y)));

		// Locate the relevant seams pixels and color the image accordingly.
		int[][] seams = this.findSeams();
		for (int y = 0; y < seams[0].length; y++) {
			for (int[] s : seams) {
				result.setRGB(s[y] ,y, seamColorRGB);
			}
		}

		return result;
	}

	public boolean[][] getMaskAfterSeamCarving() {
		boolean[][] mask = new boolean[outHeight][outWidth];
		// The image mask values are already updated.
		// Just trim it to the correct matrix size.
		setForEachOutputParameters();
		forEach((y, x) -> mask[y][x] = imageMask[y][x]);
		return mask;
	}

	/**
	 * Sets the pixels in greyscale values.
	 */
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
	 * Calculate the gradient magnitude initialization matrix
	 * by calculating the energy of each pixel.
	 */
	private void initCostMatrix(int height, int width) {
		logger.log("Initiating cost matrix...");
		EnergyPixel[][] E = new EnergyPixel[height][width];
		forEach((y, x) -> E[y][x] = new EnergyPixel(this.calcEnergy(y, x)));

		logger.log("Done initiating cost matrix by pixel energies.");
		this.costMatrix = E;
	}

	/**
	 * Calculates the Dynamic Programming recursive formula
	 * for the cost matrix.
	 */
	private void calcForwardCostMatrix() {
		logger.log("Calculating forward looking cost matrix...");
		forEach(this::calcCoordinateCost);
		logger.log("Done calculating cost matrix.");
	}

	/**
	 * Updates the cost matrix by recalculating the energies
	 * and forward cost.
	 */
	private void updateForwardCostMatrix() {
		setForEachWidth(inWidth - k);
		this.initCostMatrix(inHeight, inWidth - k);
		this.calcForwardCostMatrix();
	}

	/**
	 * For a given new seam, updates all the relevant matrices:
	 * - Image mask
	 * - Greyscale values
	 * - On reduce operation, shifts the pixels left on the temporary image.
	 * On top of that, it calls for calculation of the next cost matrix.
	 * @param seam - given seam.
	 */
	private void updateMatrices(int[] seam) {
		logger.log("Updating matrices...");

		for (int y = 0; y < costMatrix.length; y++) {
			// Shift left all pixels that are right to the seam
			for (int x = seam[y]; x < inWidth - k; x++) {
				imageMask[y][x] = imageMask[y][x + 1];
				greyscale[y][x] = greyscale[y][x + 1];

				if (opReduce) {
					// Relevant only for when reducing the image.
					tempImg.setRGB(x, y, tempImg.getRGB(x + 1, y));
				}
			}
		}

		this.updateForwardCostMatrix();
		logger.log("Done updating matrices for seam #" + k);
	}

	/**
	 * Calculates the energy of a pixel using gradient magnitude.
	 * @param y - y coordinate
	 * @param x - x coordinate
	 * @return (long) the resulted energy.
	 */
	private long calcEnergy(int y, int x) {
		int neighborX = (x < inWidth - 1 - k) ? x + 1 : x - 1;
		int neighborY = (y < inHeight - 1) ? y + 1 : y - 1;

		long energy;
		if (imageMask[y][x]) {
			return Integer.MIN_VALUE;
		}

		int deltaX = greyscale[y][neighborX] - greyscale[y][x];
		int deltaY = greyscale[neighborY][x] - greyscale[y][x];
		return Math.abs(deltaX) + Math.abs(deltaY);
	}

	/**
	 * Find all seams.
	 * @return a matrix of the original seams indexes.
	 */
	private int[][] findSeams() {
		logger.log("Searching seams...");
		// Seams indexes that correspond to the dynamic programming cost matrix.
		shiftedSeams = new int[numOfSeams][inHeight];

		// Seams indexes that correspond to their indexes on increasing operation.
		increasedSeams = new int[numOfSeams][inHeight];

		// Seams indexes that represent their original indexes.
		int [][] seams = new int[numOfSeams][inHeight];

		this.setGreyscale();
		this.initCostMatrix(inHeight, inWidth);
		this.calcForwardCostMatrix();

		// In increase mode, while calculating indexes for each new seam,
		// we want to check how many seams are preceding to the new ones,
		// by comparing the indexes values.
		// Therefore, initiate the relative 'cost matrix seams' indexes to
		// large values to avoid counting empty 'zero' index seams.
		for (int[] shiftedSeam : shiftedSeams) {
			for (int j = 0; j < shiftedSeam.length; j++) {
				shiftedSeam[j] = Integer.MAX_VALUE;
			}
		}

		for (k = 1; k <= numOfSeams; ++k) {
			int[] seam = findSeam();
			this.updateMatrices(seam);

			if (!opReduce) {
				// Relevant for increasing and showing the seams.
				seams[k - 1] = this.restoreSeamIdxs(seam);
				increasedSeams[k - 1] = this.calculateIncreasedSeamIdxs(seam);
			}

			shiftedSeams[k - 1] = seam;
		}
		logger.log("Done searching for new seams.");
		return seams;
	}

	/**
	 * By starting with the minimum value in the last row for
	 * the cost matrix, backtrace the path of the seam.
	 * @return - returns the seam indexes.
	 */
	private int[] findSeam() {
		logger.log("Finding optimal seam #" + k);
		int[] seam = new int[inHeight];

		// Get the index of the minimum cost value from the
		// last row of the cost matrix.
		int j = inHeight - 1;
		int idx = this.findMinCostIdx(this.costMatrix[j]);

		// Start from bottom and climb up using each
		// pixel's parent value.
		for (int i = seam.length - 1; i >= 0; i--) {
			seam[i] = idx;
			path p = costMatrix[j][idx].getParent();
			if (p == path.L) { idx -= 1; }
			else if (p == path.R) { idx += 1; }
			j--;
		}

		return seam;
	}

	/**
	 * Find the index of the minimum value for a given
	 * array of pixels.
	 * @param arr - given EnergyPixel array.
	 * @return the resulted index.
	 */
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

	/**
	 * Calculates the actual seam indexes when increasing the image.
	 * This is done by checking how much seams are preceding to
	 * the given seam.
	 * @param seam
	 * @return
	 */
	private int[] calculateIncreasedSeamIdxs(int[] seam){
		int[] expected = new int[seam.length];

		for (int i = 0; i < seam.length; i++){
			int s = 0; // Number of seams preceding current seam
			for (int[] shiftedSeam : shiftedSeams) {
				if (seam[i] >= shiftedSeam[i]) s++;
			}
			expected[i] = seam[i] + (2 * s);
		}

		// If the new seam has a smaller x coordinate then the
		// previous ones, shift them by 1 to the right.
		for (int i = 0; i < seam.length; i++) {
			for (int[] s : increasedSeams) {
				if (expected[i] <= s[i]) s[i]++;
			}
		}

		return expected;
	}

	/**
	 * Calculates the original indexes of a given seam (which indexes
	 * are relative to the current cost matrix).
	 * @param seam
	 * @return
	 */
	private int[] restoreSeamIdxs(int[] seam) {
		int[] restored = new int[seam.length];

		for (int i = 0; i < seam.length; i++) {
			int s = 0; // Number of seams preceding current seam
			for (int[] shiftedSeam : shiftedSeams) {
				if (seam[i] >= shiftedSeam[i]) s++;
			}
			restored[i] = seam[i] + s;
		}

		return restored;
	}

	/**
	 * Calculates and sets the cost for a given coordinate.
	 */
	private void calcCoordinateCost(int y, int x) {
		if (y == 0) {
			return; // Avoid calculating for base case - first row.
		}

		long left = Integer.MAX_VALUE;
		long right = Integer.MAX_VALUE;

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

	/**
	 * Returns the chosen parent path by the minimum value.
	 */
	private path getPathByMinimum(long a, long b, long c) {
		long minVal = Math.min(a, Math.min(b, c));
		if (minVal == a) return path.L;
		if (minVal == b) return path.V;
		return path.R;
	}
}
