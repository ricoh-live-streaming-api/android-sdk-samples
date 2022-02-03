using System;
using UnityEngine;

public static class JwtAccessToken
{
    private readonly static DateTime UnixEpoch = new DateTime(1970, 1, 1, 0, 0, 0, 0, DateTimeKind.Utc);

    /**
     * サンプルのためクライアントサイドに生成関数を追加していますが、
     * AccessTokenはアプリバックエンドを用意して生成してください。
     */
    public static string CreateAccessToken(
        string clientSecret,
        string roomId,
        RoomSpec roomSpec)
    {
        byte[] guid = Guid.NewGuid().ToByteArray();
        string connectionId = Convert.ToBase64String(guid, 0, guid.Length)
            .Replace("=", "")
            .Replace("+", "")
            .Replace("/", "");
        byte[] secretByteArray = System.Text.Encoding.UTF8.GetBytes(clientSecret);

        using (var keys = new AndroidJavaClass("io.jsonwebtoken.security.Keys"))
        using (var key = keys.CallStatic<AndroidJavaObject>("hmacShaKeyFor", secretByteArray))
        using (var algorithm = new AndroidJavaClass("io.jsonwebtoken.SignatureAlgorithm"))
        using (var hs256 = algorithm.GetStatic<AndroidJavaObject>("HS256"))
        using (var jwts = new AndroidJavaClass("io.jsonwebtoken.Jwts"))
        using (var builder = jwts.CallStatic<AndroidJavaObject>("builder"))
        {
            DateTimeOffset nbf = DateTimeOffset.Now.AddMinutes(-30);

            builder.Call<AndroidJavaObject>("claim", "nbf", Utils.GetJavaObject((long)(nbf.ToUniversalTime() - UnixEpoch).TotalSeconds));
            builder.Call<AndroidJavaObject>("claim", "exp", Utils.GetJavaObject((long)(nbf.AddHours(1).ToUniversalTime() - UnixEpoch).TotalSeconds));
            builder.Call<AndroidJavaObject>("claim", "room_id", Utils.GetJavaObject(roomId));
            builder.Call<AndroidJavaObject>("claim", "connection_id", Utils.GetJavaObject("Untiy" + connectionId));
            builder.Call<AndroidJavaObject>("claim", "room_spec", roomSpec.GetSpecHashMap());
            builder.Call<AndroidJavaObject>("signWith", key, hs256);
            return builder.Call<string>("compact");
        }
    }

}
