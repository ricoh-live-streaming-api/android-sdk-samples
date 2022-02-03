using System.Collections;
using UnityEngine;
using UnityEngine.Android;
using UnityEngine.SceneManagement;

/// <summary>
/// パーミッション設定(Unity2018.3以降)
/// </summary>
class PermissionAuthorization : MonoBehaviour
{
    IEnumerator Start()
    {
#if UNITY_ANDROID
        // for Android
        Debug.Log("PermissionAuthorization Start");

        // カメラのパーミッションが許可されているか？
        if (!Permission.HasUserAuthorizedPermission(Permission.Camera))
        {
            Debug.Log("RequestUserPermission Camera");

            // 権限がないためカメラのパーミッションをリクエストする
            yield return RequestUserPermission(Permission.Camera);
        }
        else
        {
            Debug.Log("Authorized Camera");
        }

        // マイクのパーミッションが許可されているか？
        if (!Permission.HasUserAuthorizedPermission(Permission.Microphone))
        {
            Debug.Log("RequestUserPermission Microphone");

            // 権限がないためマイクのパーミッションをリクエストする
            yield return RequestUserPermission(Permission.Microphone);
        }
        else
        {
            Debug.Log("Authorized Microphone");
        }

        // ストレージ書き込みのパーミッションが許可されているか？
        if (!Permission.HasUserAuthorizedPermission(Permission.ExternalStorageWrite))
        {
            Debug.Log("RequestUserPermission ExternalStorageWrite");

            // 権限がないためストレージ書き込みのパーミッションをリクエストする
            yield return RequestUserPermission(Permission.ExternalStorageWrite);
        }
        else
        {
            Debug.Log("Authorized ExternalStorageWrite");
        }

        // リクエストの結果、アプリ機能に必要なパーミッションが全て許可されたか調べる
        if (Permission.HasUserAuthorizedPermission(Permission.Camera) 
            && Permission.HasUserAuthorizedPermission(Permission.Microphone)
            && Permission.HasUserAuthorizedPermission(Permission.ExternalStorageWrite)
            )
        {
            Debug.Log("Authorized All");

            // 権限が許可されたので、サンプルシーンに飛ばす
            SceneManager.LoadScene("SampleScene");
        }
        else
        {
            Debug.Log("Non Authorized");

            // 権限が許可されなかったので、ユーザーに対して権限の使用用途の説明を表示してから再度のリクエストを行う。
            // もしも拒否時に「今後表示しない」がチェックされた場合は、次回からリクエスト自体が表示されなくなる、
            // そのためユーザーには自分でOSのアプリ設定画面で権限許可を行うようにアナウンスする必要がある。
            // （Permissionクラスにはそれらの違いを調べる方法は用意されていない）
            Utils.Toast("Please grant all permission.");

            // アプリケーション終了
            Application.Quit();
        }
#endif
        yield break;
    }

#if UNITY_ANDROID
    private bool isRequesting;

    IEnumerator RequestUserPermission(string permission)
    {
        Debug.Log("RequestUserPermission " + permission);

        isRequesting = true;

        Permission.RequestUserPermission(permission);
        // Androidでは「今後表示しない」をチェックされた状態だとダイアログは表示されないが、フォーカスイベントは通常通り発生する模様。
        // したがってタイムアウト処理は本来必要ないが、万が一の保険のために一応やっとく。

        // アプリフォーカスが戻るまで待機する
        float timeElapsed = 0;
        while (isRequesting)
        {
            if (timeElapsed > 0.5f)
            {
                isRequesting = false;
                yield break;
            }
            timeElapsed += Time.deltaTime;

            yield return null;
        }
        yield break;
    }
#endif
}

