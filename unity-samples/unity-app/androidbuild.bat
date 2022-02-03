@rem Unityアプリケーションパス
set UNITY_APP_PATH="C:\Program Files\Unity\Hub\Editor\2019.3.7f1\Editor\Unity.exe"

@rem 対象のUnityプロジェクトパス
set UNITY_PROJECT_PATH="C:\Works\Android\cappella-android\rdc-unity-app"

@rem バッチモードで起動後に呼び出すメソッド
set UNITY_BATCH_EXECUTE_METHOD=BatchBuild.Build

@rem Unity Editor ログファイルパス
set UNITY_EDITOR_LOG_PATH="C:\Temp_AndroidBuild\Editor.log"

@rem 指定のUnityプロジェクトをバッチモード起動させて、指定のメソッド(Unity上で用意する)を呼び出す
%UNITY_APP_PATH% -batchmode -quit -projectPath %UNITY_PROJECT_PATH% -executeMethod %UNITY_BATCH_EXECUTE_METHOD% -logfile -platform Android -isRelease false

exit 0
