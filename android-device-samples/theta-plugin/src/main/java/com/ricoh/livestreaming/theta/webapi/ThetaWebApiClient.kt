/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.theta.webapi

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

object ThetaWebApiClient {
    // THETA Web Api のコマンド実行 URL
    private const val url = "http:127.0.0.1:8080/osc/commands/execute"

    private val gson = Gson()
    private val okHttpClient = OkHttpClient()

    fun getDateTimeZone(): String {
        val body = mapOf(
                "name" to "camera.getOptions",
                "parameters" to mapOf(
                        "optionNames" to listOf(
                                "dateTimeZone"
                        )
                )
        )

        val response = this.executeCommand(body)

        response.body!!.charStream().use { r ->
            return gson.fromJson(r, JsonObject::class.java)["results"]
                    .asJsonObject["options"]
                    .asJsonObject["dateTimeZone"].asString
        }
    }

    private fun executeCommand(requestParams: Map<String, Any>): Response {
        val requestBody = gson.toJson(requestParams).toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull())
        val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Accept", "application/json")
                .addHeader("X-XSRF-Protected", "1")
                .build()

        val response = okHttpClient.newCall(request).execute()

        if (response.code == HTTP_UNAUTHORIZED) {
            throw IOException(response.message)
        }

        return response
    }
}
