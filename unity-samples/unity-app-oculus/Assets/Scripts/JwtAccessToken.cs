using System;
using UnityEngine;

public static class JwtAccessToken
{
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

            builder.Call<AndroidJavaObject>("claim", "nbf", Utils.GetJavaObject(nbf.ToUnixTimeSeconds()));
            builder.Call<AndroidJavaObject>("claim", "exp", Utils.GetJavaObject(nbf.AddHours(1).ToUnixTimeSeconds()));
            builder.Call<AndroidJavaObject>("claim", "room_id", Utils.GetJavaObject(roomId));
            builder.Call<AndroidJavaObject>("claim", "connection_id", Utils.GetJavaObject("AndroidUnityQuest" + connectionId));
            builder.Call<AndroidJavaObject>("claim", "room_spec", roomSpec.GetSpecHashMap());
            builder.Call<AndroidJavaObject>("signWith", key, hs256);
            return builder.Call<string>("compact");
        }
    }

}
