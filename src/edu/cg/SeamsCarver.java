package edu.cg;

import java.awt.*;
import java.awt.image.BufferedImage;

public class SeamsCarver extends ImageProcessor {

	// MARK: An inner interface for functional programming.
	@FunctionalInterface
	interface ResizeOperation {
		BufferedImage resize();
	}

	// MARK: Fields
	private int numOfSeams;
	private ResizeOperation resizeOp;
	boolean[][] imageMask;
	// TODO: Add some additional fields

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
		// Calculate the gradient magnitude matrix.
		long[][] E = new long[inHeight][inWidth];
		long[][] M = new long[inHeight][inWidth];
		int[][] path = new int[inHeight][inWidth];
		BufferedImage greyscale = newEmptyInputSizedImage();

		//
		forEach((y, x) -> {
			Color c =  new Color(workingImage.getRGB(x, y));
			int greyVal = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
			Color greyCol = new Color(greyVal, greyVal, greyVal);
			greyscale.setRGB(x, y, greyCol.getRGB());
		});

		forEach((y, x) -> {
			int neighborX = (x < inWidth - 2) ? x + 1 : x - 1;
			int neighborY = (y < inHeight - 2) ? y + 1 : y - 1;

			int deltaX = greyscale.getRGB(neighborX, y) - greyscale.getRGB(x,y);
			int deltaY = greyscale.getRGB(x , neighborY) - greyscale.getRGB(x,y);
			E[y][x] = (int) Math.sqrt((1 << deltaX) + (1 << deltaY));
			// TODO: add MASK values.
			M[y][x] = E[y][x];

		});

		forEach((y, x) -> {
			// Avoid calculating for base case - first row.
			if (y != 0) {
				long left = Long.MAX_VALUE;
				long right = Long.MAX_VALUE;

				// All cases
				long center = M[y - 1][x] + Math.abs(greyscale.getRGB(x + 1, y) - greyscale.getRGB(x - 1, y));

				// Excluding first column
				if (x != 0) {
					int cl = Math.abs(greyscale.getRGB(x + 1, y) - greyscale.getRGB(x - 1, y));
					cl += Math.abs(greyscale.getRGB(x, y - 1) - greyscale.getRGB(x - 1, y));

					left = M[y - 1][x - 1] + cl;
				}

				// Excluding last column
				if (x != inWidth - 1) {
					int cr = Math.abs(greyscale.getRGB(x + 1, y) - greyscale.getRGB(x - 1, y));
					cr += Math.abs(greyscale.getRGB(x + 1, y) - greyscale.getRGB(x, y - 1));

					right = M[y - 1][x + 1] + cr;
				}

				int minIdx = getMinimumIndex(left, center, right);

				long val;
				if (minIdx == -1) val = left;
				else if (minIdx == 0) val = center;
				else val = right;

				M[y][x] = E[y][x] + val;
				path[y][x] = minIdx; // Chosen parent direction.
			}
		});
		// return...
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

	private int getMinimumIndex(long a, long b, long c) {
		long minVal = Math.min(a, Math.min(b, c));
		if (minVal == a) return -1;
		if (minVal == b) return 0;
		return 1;
	}
}
