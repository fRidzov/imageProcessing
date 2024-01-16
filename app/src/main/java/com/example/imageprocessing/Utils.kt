package com.example.imageprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt


fun getMean(bitmap: Bitmap): Float {
    val width = bitmap.width
    val height = bitmap.height

    // Convert the Bitmap to grayscale values and calculate the mean
    var sum = 0.0f
    for (i in 0 until height) {
        for (j in 0 until width) {
            sum += bitmap.getPixel(j, i) / 255.0f
        }
    }

    // Calculate the mean of grayscale values
    val mean = sum / (width * height)

    return mean
}

fun getVariance(bitmap: Bitmap, mean: Float): Float {
    val width = bitmap.width
    val height = bitmap.height

    // Calculate the variance of grayscale values
    var sum = 0.0
    for (i in 0 until height) {
        for (j in 0 until width) {
            val pixelValue = bitmap.getPixel(j, i) / 255.0f
            sum += (pixelValue - mean).toDouble().pow(2)
        }
    }

    val variance = sum / (width * height)

    return variance.toFloat()
}

fun getStandardDeviation(bitmap: Bitmap, variance: Float): Float {
    return sqrt(variance.toDouble()).toFloat()
}


fun generateGaussianNoise(
    mean: Float,
    stdDev: Float,
    width: Int,
    height: Int
): List<List<Double>> {
    val random = java.util.Random()

    val resultList = mutableListOf<List<Double>>()

    repeat(width) { i ->
        val row = mutableListOf<Double>()
        repeat(height) { j ->
            var u1: Double
            var u2: Double
            var z0: Double

            do {
                u1 = 2.0 * random.nextDouble() - 1.0
                u2 = 2.0 * random.nextDouble() - 1.0
                z0 = u1 * u1 + u2 * u2
            } while (z0 >= 1.0 || z0 == 0.0)

            val z = sqrt(-2.0 * ln(z0) / z0)
            val sample = mean + stdDev * u1 * z
            row.add(sample)
        }

        resultList.add(row)
    }

    return resultList
}


fun addGaussianAdditiveNoiseToBitmap(bitmap: Bitmap, stdDev: Float, mean: Float): Bitmap {
    val width = bitmap.width
    val height = bitmap.height

    val noisyBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(noisyBitmap)
    canvas.drawBitmap(bitmap, 0f, 0f, null)

    val noise = generateGaussianNoise(mean, stdDev, width, height)

    for (i in 0 until width) {
        for (j in 0 until height) {
            val pixel = bitmap.getPixel(i, j)

            // Extracting RGB components
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)

            // Adding Gaussian noise to each RGB component
            val noisyRed = (red + noise[i][j]).coerceIn(0.0, 255.0).toInt()
            val noisyGreen = (green + noise[i][j]).coerceIn(0.0, 255.0).toInt()
            val noisyBlue = (blue + noise[i][j]).coerceIn(0.0, 255.0).toInt()

            // Creating the noisy pixel
            val noisyPixel = Color.rgb(noisyRed, noisyGreen, noisyBlue)

            // Drawing the noisy pixel on the canvas
            canvas.drawPoint(i.toFloat(), j.toFloat(), Paint().apply { color = noisyPixel })
        }
    }
    return noisyBitmap
}

fun getMSE(bitmap1: Bitmap, bitmap2: Bitmap): Double {
    require(bitmap1.width == bitmap2.width && bitmap1.height == bitmap2.height) { "Bitmaps must have the same dimensions" }

    val width = bitmap1.width
    val height = bitmap1.height

    var mse = 0.0

    for (i in 0 until width) {
        for (j in 0 until height) {
            val pixel1 = bitmap1.getPixel(i, j)
            val pixel2 = bitmap2.getPixel(i, j)

            val red1 = Color.red(pixel1)
            val green1 = Color.green(pixel1)
            val blue1 = Color.blue(pixel1)

            val red2 = Color.red(pixel2)
            val green2 = Color.green(pixel2)
            val blue2 = Color.blue(pixel2)

            mse += (red1 - red2).toDouble().pow(2) +
                    (green1 - green2).toDouble().pow(2) +
                    (blue1 - blue2).toDouble().pow(2)
        }
    }

    mse /= (width * height * 3)  // Divide by 3 for the three color channels (RGB)

    return mse
}

fun getRMSE(bitmap1: Bitmap, bitmap2: Bitmap): Double {
    require(bitmap1.width == bitmap2.width && bitmap1.height == bitmap2.height) { "Bitmaps must have the same dimensions" }
    val mse = getMSE(bitmap1, bitmap2)
    val rmse = sqrt(mse)
    return rmse
}

const val MAX_NUMBER_OF_INTENSITY_LEVELS = 256

fun getPSNR(bitmap1: Bitmap, bitmap2: Bitmap): Double {
    val rmse = getRMSE(bitmap1, bitmap2)
    val psnr = 20 * log10((MAX_NUMBER_OF_INTENSITY_LEVELS - 1) / sqrt(rmse))
    return psnr
}

fun createFilterKernel(weights: FilterKernel): FilterKernel {
    val filterKernelSum = weights.sumOf { it.sum() }

    if (filterKernelSum != 1.0) {
        for (i in weights.indices) {
            for (j in weights[i].indices) {
                weights[i][j] /= filterKernelSum
            }
        }
    }

    return weights
}

fun linearSpatialFiltering(originalBitmap: Bitmap, filterKernel: FilterKernel): Bitmap {
    val N = originalBitmap.width
    val M = originalBitmap.height

    val filterKernelSizes = Pair(filterKernel.size, filterKernel[0].size)

    val extendedImageFunction = getMirroredImageFunction(originalBitmap, filterKernelSizes)

    val a = filterKernelSizes.first / 2
    val b = filterKernelSizes.second / 2

    val filteredImage = Bitmap.createBitmap(N, M, Bitmap.Config.ARGB_8888)

    for (i in 0 until N) {
        for (j in 0 until M) {
            var sumRed = 0.0
            var sumGreen = 0.0
            var sumBlue = 0.0

            for (s in -a..a) {
                for (t in -b..b) {
                    val pixel = extendedImageFunction(i + s, j + t)
                    val weight = filterKernel[a + s][b + t]

                    sumRed += Color.red(pixel) * weight
                    sumGreen += Color.green(pixel) * weight
                    sumBlue += Color.blue(pixel) * weight
                }
            }

            val finalRed = sumRed.roundToInt().coerceIn(0, 255)
            val finalGreen = sumGreen.roundToInt().coerceIn(0, 255)
            val finalBlue = sumBlue.roundToInt().coerceIn(0, 255)

            filteredImage.setPixel(i, j, Color.rgb(finalRed, finalGreen, finalBlue))
        }
    }

    return filteredImage
}

fun getMirroredImageFunction(originalBitmap: Bitmap, filterKernelSizes: Pair<Int, Int>): (Int, Int) -> Int {
    val extendedImage = extendImageWithMirroring(originalBitmap, filterKernelSizes)
    return { x, y ->
        val clippedX = x.coerceIn(0 until originalBitmap.width)
        val clippedY = y.coerceIn(0 until originalBitmap.height)
        extendedImage.getPixel(clippedX, clippedY)
    }
}

fun extendImageWithMirroring(originalBitmap: Bitmap, filterKernelSizes: Pair<Int, Int>): Bitmap {
    val extendedImage = Bitmap.createBitmap(
        originalBitmap.width + 2 * (filterKernelSizes.first - 1) / 2,
        originalBitmap.height + 2 * (filterKernelSizes.second - 1) / 2,
        Bitmap.Config.ARGB_8888
    )

    for (i in 0 until extendedImage.width) {
        for (j in 0 until extendedImage.height) {
            val originalRow = (i - (filterKernelSizes.first - 1) / 2).coerceIn(0 until originalBitmap.width)
            val originalCol = (j - (filterKernelSizes.second - 1) / 2).coerceIn(0 until originalBitmap.height)
            extendedImage.setPixel(i, j, originalBitmap.getPixel(originalRow, originalCol))
        }
    }

    return extendedImage
}