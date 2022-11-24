/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.bluetoothdscreader

import java.util.HashMap

/**
 * This class includes the DSC GATT attributes, and a small subset of standard GATT attributes.
 */
object DSCGattUuids {
    private val attributes: HashMap<String?, String?> = HashMap<String?, String?>()

    // Invented by Doug
    var DOUG_DSC_SERVICE = "4fafc201-1fb5-459e-8fcc-c5c9c331924b"
    var AZIMUTH = "beb5483e-36e1-4688-b7f5-ea07361b26ba"
    var ELEVATION = "beb5483e-36e1-4688-b7f5-ea07361b265e"
    var AZEL = "beb5483e-36e1-4688-b7f5-ea07361b26ae"
    var AZ_RESOLUTION = "beb5483e-36e1-4688-b7f5-ea07361b26ca"
    var EL_RESOLUTION = "beb5483e-36e1-4688-b7f5-ea07361b266e"
    var RESET = "beb5483e-36e1-4688-b7f5-ea07361b2168"
    var RESET90 = "beb5483e-36e1-4688-b7f5-ea07361b2169"

   @JvmStatic
    fun lookup(uuid: String?, defaultName: String): String {
        val name = attributes[uuid]
        return name ?: defaultName
    }

    init {
        // Standard Services.
        attributes["0000180a-0000-1000-8000-00805f9b34fb"] = "Device Information Service"

        // Invented by Doug
        attributes[DOUG_DSC_SERVICE] = "Doug DSC Service"

        // Standard Characteristics.
        attributes["00002902-0000-1000-8000-00805f9b34fb"] = "Client Characteristic Config"
        attributes["00002901-0000-1000-8000-00805f9b34fb"] = "Description String"
        attributes["00002a29-0000-1000-8000-00805f9b34fb"] = "Manufacturer Name String"

        // Invented by Doug
        attributes[AZIMUTH] = "Azimuth"
        attributes[ELEVATION] = "Elevation"
        attributes[AZEL] = "Az+El"

        attributes[AZ_RESOLUTION] = "Az Resolution"
        attributes[EL_RESOLUTION] = "El Resolution"
    }
}