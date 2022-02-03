Shader "Hidden/OESVideoShader"
{
    Properties
    {
        _MainTex ("Texture", 2D) = "white" {}
    }
        SubShader
    {
        Tags { "RenderType" = "Opaque" }
        LOD 100

        Pass
        {

            GLSLPROGRAM

            #extension GL_OES_EGL_image_external : require
            #pragma glsl_es2

#ifdef VERTEX
            varying vec2 textureCoord;
            void main()
            {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                textureCoord = vec2(1.0 - gl_MultiTexCoord0.x, gl_MultiTexCoord0.y);
            }
#endif

#ifdef FRAGMENT

            varying vec2 textureCoord;
            uniform samplerExternalOES _MainTex;
            void main()
            {
                gl_FragColor = textureExternal(_MainTex, textureCoord);
            }
#endif
            ENDGLSL
        }
    }

FallBack Off

}
