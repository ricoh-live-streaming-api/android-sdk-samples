Shader "UI/YUVVideoShader"
{
    Properties
    {
        _MainTex ("Texture", 2D) = "white" {}
        _UTex ("Texture", 2D) = "white" {}
        _VTex("Texture", 2D) = "white" {}
    }
    SubShader
    {
        Tags { "RenderType"="Opaque" }
        LOD 100

        Pass
        {
            CGPROGRAM
            #pragma vertex vert
            #pragma fragment frag
            // make fog work
            #pragma multi_compile_fog

            #include "UnityCG.cginc"

            struct appdata
            {
                float4 vertex : POSITION;
                float2 uv : TEXCOORD0;
            };

            struct v2f
            {
                float2 uvPos : TEXCOORD0;
                UNITY_FOG_COORDS(1)
                float4 vertex : SV_POSITION;
            };

            sampler2D _MainTex;
            sampler2D _UTex;
            sampler2D _VTex;
            float4 _MainTex_ST;

            v2f vert (appdata v)
            {
                v2f o;
                o.vertex = UnityObjectToClipPos(v.vertex);
                o.uvPos = TRANSFORM_TEX(v.uv, _MainTex);

#if UNITY_UV_STARTS_AT_TOP
                o.uvPos.y = 1 - o.uvPos.y;
#endif
                return o;
            }

            fixed4 frag(v2f i) : SV_Target
            {
                float y = tex2D(_MainTex, i.uvPos).a * 1.16438;
                float u = tex2D(_UTex, i.uvPos).a;
                float v = tex2D(_VTex, i.uvPos).a;

                float r = y + 1.59603 * v - 0.874202;
                float g = y - 0.391762 * u - 0.812968 * v + 0.531668;
                float b = y + 2.01723 * u - 1.08563;

                return float4(r, g, b, 1.0);
            }

            ENDCG
        }
    }
}
