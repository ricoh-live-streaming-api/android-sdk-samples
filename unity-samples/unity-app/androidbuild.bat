@rem Unity�A�v���P�[�V�����p�X
set UNITY_APP_PATH="C:\Program Files\Unity\Hub\Editor\2019.3.7f1\Editor\Unity.exe"

@rem �Ώۂ�Unity�v���W�F�N�g�p�X
set UNITY_PROJECT_PATH="C:\Works\Android\cappella-android\rdc-unity-app"

@rem �o�b�`���[�h�ŋN����ɌĂяo�����\�b�h
set UNITY_BATCH_EXECUTE_METHOD=BatchBuild.Build

@rem Unity Editor ���O�t�@�C���p�X
set UNITY_EDITOR_LOG_PATH="C:\Temp_AndroidBuild\Editor.log"

@rem �w���Unity�v���W�F�N�g���o�b�`���[�h�N�������āA�w��̃��\�b�h(Unity��ŗp�ӂ���)���Ăяo��
%UNITY_APP_PATH% -batchmode -quit -projectPath %UNITY_PROJECT_PATH% -executeMethod %UNITY_BATCH_EXECUTE_METHOD% -logfile -platform Android -isRelease false

exit 0
