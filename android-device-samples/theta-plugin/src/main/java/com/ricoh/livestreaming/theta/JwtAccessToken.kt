/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.theta

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.util.*

class JwtAccessToken {
    companion object {
        /**
         * サンプルのためクライアントサイドに生成関数を追加していますが、
         * AccessTokenはアプリバックエンドを用意して生成してください。
         */
        fun createAccessToken(
                clientSecret: String,
                roomId: String,
                roomSpec: RoomSpec,
                now: Date,
                connectionId: String): String {
            val key = Keys.hmacShaKeyFor(clientSecret.toByteArray(StandardCharsets.UTF_8))

            val cal = Calendar.getInstance()
            cal.time = now
            cal.add(Calendar.MINUTE, -30)
            val nbf = cal.timeInMillis / 1000
            cal.add(Calendar.HOUR, 1)
            val exp = cal.timeInMillis / 1000

            return Jwts.builder()
                    .claim("nbf", nbf)
                    .claim("exp", exp)
                    .claim("room_id", roomId)
                    .claim("room_spec", roomSpec.getSpec())
                    .claim("connection_id", connectionId)
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact()
        }
    }
}
