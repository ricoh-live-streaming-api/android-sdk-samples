#if UNITY_EDITOR

using System.Collections;
using System.Collections.Generic;
using UnityEditor;
using UnityEditor.Build.Content;
using UnityEngine;

public class BatchBuild
{
	private static BuildTarget buildTarget;
	private static bool releaseFlag = false;

	private static string keystorePath = "testtest.keystore";
	private static string keystorePass = "testtest";
	private static string keyaliesName = "testtest";
	private static string keyaliesPass = "testtest";

	/// <summary>
	/// コマンドラインから呼ばれる関数
	/// </summary>
	public static void Build()
	{
		GetCommandLineArgs();

		// Androidの場合
		if (buildTarget == BuildTarget.Android)
		{
			bool status = BuildAndroid(releaseFlag);
			EditorApplication.Exit(status ? 0 : 1);
		}
	}

	/// <summary>
	/// Android用ビルド
	/// </summary>
	/// <param name="isRelease"></param>
	/// <returns></returns>
	private static bool BuildAndroid(bool isRelease = false)
	{
		Debug.Log("[BuildLog] Start Build for Android");

		BuildOptions buildOptions = BuildOptions.None;

		// リリースビルドでない場合はプロファイラなどにつなげるようにする
		if (!isRelease)
		{
			buildOptions |= BuildOptions.Development | BuildOptions.ConnectWithProfiler | BuildOptions.AllowDebugging;
		}

		// KeyStoreの設定
		SetAndroidKeyStoreInfo();

		string[] scenes = GetEnableScenesName();

		// ビルド
		var report = BuildPipeline.BuildPlayer(scenes, "./livestreaming.apk", buildTarget, buildOptions);
		
		if (report.summary.result != UnityEditor.Build.Reporting.BuildResult.Succeeded)
		{
			Debug.Log("[BuildLog] Failed Build Android");

			return false;
		}

		Debug.Log("[BuildLog] Success Build Android");

		return true;
	}

	/// <summary>
	/// KeyStoreの設定
	/// </summary>
	private static void SetAndroidKeyStoreInfo()
	{
		// KeyStoreの場所とパスワードの設定
		PlayerSettings.Android.keystoreName = Application.dataPath + "/../" + keystorePath;
		PlayerSettings.Android.keystorePass = keystorePass;

		// エイリアスの名前とパスワードの設定
		PlayerSettings.Android.keyaliasName = keyaliesName;
		PlayerSettings.Android.keyaliasPass = keyaliesPass;
	}

	/// <summary>
	/// 引数を解析して変数に代入する
	/// </summary>
	private static void GetCommandLineArgs()
	{
		string[] args = System.Environment.GetCommandLineArgs();

		for(int i = 0; i < args.Length; i++)
		{
			switch (args[i])
			{
				case "-platform":	// ターゲットプラットフォーム
					buildTarget = (BuildTarget)System.Enum.Parse(typeof(BuildTarget), args[i + 1]);
					break;

				case "-isRelease":  // リリース or デバッグ
					releaseFlag = bool.Parse(args[i + 1]);
					break;

				default:
					break;
			}
		}
	}

	/// <summary>
	/// BuildSettingで有効になっているScene名を取得する
	/// </summary>
	/// <returns>BuildSettingで有効になっているScene名</returns>
	private static string[] GetEnableScenesName()
	{
		EditorBuildSettingsScene[] scenes = EditorBuildSettings.scenes;
		List<string> sceneNameList = new List<string>();

		for(int i = 0; i < scenes.Length; i++)
		{
			if (scenes[i].enabled)
			{
				sceneNameList.Add(scenes[i].path);
			}
		}

		return sceneNameList.ToArray();
	}
}

#endif