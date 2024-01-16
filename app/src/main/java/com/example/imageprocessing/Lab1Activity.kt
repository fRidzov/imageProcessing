package com.example.imageprocessing

import android.content.Context

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.imageprocessing.databinding.ActivityLab1Binding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

typealias FilterKernel = Array<DoubleArray>

class Lab1Activity : AppCompatActivity() {

    private lateinit var binding: ActivityLab1Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLab1Binding.inflate(layoutInflater)
        setContentView(binding.root)

        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.lena_gray_256)
        binding.input.setImageBitmap(bitmap)
        val mean = getMean(bitmap)
        val variance = getVariance(bitmap, mean)
        val standardDeviation = getStandardDeviation(bitmap, variance)
        binding.info.text =
            " mean-$mean \n variance-$variance \n standardDeviation-$standardDeviation"

        val stdDevCoefs = doubleArrayOf(0.2, 0.3)
        val noisyImages = stdDevCoefs.map { addGaussianAdditiveNoiseToBitmap(bitmap, standardDeviation, mean) }

        binding.noisy1.setImageBitmap(noisyImages[0])
        binding.noisy2.setImageBitmap(noisyImages[1])
        val noisyRMSEs = noisyImages.map { getRMSE(bitmap, it) }
        val noisyPSNRs = noisyImages.map { getPSNR(bitmap, it) }

        binding.noisy1Info.text = "RMSE-${noisyRMSEs[0]}\nPSNR-${noisyPSNRs[0]}"
        binding.noisy2Info.text = "RMSE-${noisyRMSEs[1]}\nPSNR-${noisyPSNRs[1]}"

        var filterKernels: List<FilterKernel> = listOf(
            arrayOf(
                doubleArrayOf(1.0, 2.0, 1.0),
                doubleArrayOf(2.0, 4.0, 2.0),
                doubleArrayOf(1.0, 2.0, 1.0)
            ),
            arrayOf(
                doubleArrayOf(1.0, 15.0, 1.0),
                doubleArrayOf(15.0, 30.0, 15.0),
                doubleArrayOf(1.0, 15.0, 1.0)
            )
        )
        filterKernels = filterKernels.map { createFilterKernel(it) }.toList()

        val filteredImages = filterKernels.map { filterKernel ->
            noisyImages.map { noisyImage ->
                linearSpatialFiltering(noisyImage, filterKernel)
            }
        }
        val filteredRMSEs = filteredImages.map {
            it.map {
                getRMSE(bitmap, it)
            }
        }
        val filteredPSNRs = filteredImages.map {
            it.map {
                getPSNR(bitmap, it)
            }
        }

        binding.filtered1.setImageBitmap(filteredImages[0][0])
        binding.filtered1Info.text = "RMSE-${filteredRMSEs[0][0]}\nPSNR-${filteredPSNRs[0][0]}"

        binding.filtered2.setImageBitmap(filteredImages[0][1])
        binding.filtered2Info.text = "RMSE-${filteredRMSEs[0][1]}\nPSNR-${filteredPSNRs[0][1]}"
    }
}