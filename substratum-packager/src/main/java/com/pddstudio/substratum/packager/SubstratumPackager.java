package com.pddstudio.substratum.packager;

import android.content.Context;
import android.util.Log;

import com.pddstudio.substratum.packager.models.ApkInfo;
import com.pddstudio.substratum.packager.models.AssetFileInfo;
import com.pddstudio.substratum.packager.models.AssetsType;
import com.pddstudio.substratum.packager.utils.AssetUtils;
import com.pddstudio.substratum.packager.utils.ZipUtils;

import org.androidannotations.annotations.AfterInject;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EBean;
import org.zeroturnaround.zip.commons.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java8.util.stream.StreamSupport;

/**
 * Created by pddstudio on 20/04/2017.
 */

@EBean(scope = EBean.Scope.Singleton)
public class SubstratumPackager {

	private static final String TAG = SubstratumPackager.class.getSimpleName();

	private static final String SOURCES_ZIP    = "source.zip";
	private static final String SUBSTRATUM_ZIP = "substratum.zip";

	private static final String OVERLAYS_DIR       = "overlays";
	private static final String AUDIO_DIR          = "audio";
	private static final String FONTS_DIR          = "fonts";
	private static final String BOOT_ANIMATION_DIR = "bootanimation";

	private static final String ASSETS_DIR = "assets";

	private File          cacheDir;
	private Context       context;
	private List<File>    assetDirs;
	private List<ApkInfo> apkInfoList;

	@Bean
	protected ApkExtractor apkExtractor;

	private SubstratumPackager applyConfig(Builder builder) {
		this.cacheDir = builder.cacheDir;
		this.context = builder.context.getApplicationContext();
		this.assetDirs = builder.assetDirs;
		this.apkInfoList = builder.apkInformationList;
		return this;
	}

	private boolean unzipDefaultArchives() {
		try {
			//cleanCache();
			boolean copySources = AssetUtils.copyFromAssetsToCache(cacheDir, context.getAssets(), SOURCES_ZIP);
			boolean copySubs = AssetUtils.copyFromAssetsToCache(cacheDir, context.getAssets(), SUBSTRATUM_ZIP);
			return copySources && copySubs;
		} catch (IOException io) {
			io.printStackTrace();
			return false;
		}
	}

	@AfterInject
	protected void loadApks() {
		apkInfoList = apkExtractor.apkHelper.getInstalledApks();
		StreamSupport.stream(apkInfoList).forEach(apkInfo -> Log.d(TAG, "APK: " + apkInfo));
	}

	public ApkInfo getApkInfo(String packageName) {
		return StreamSupport.stream(apkExtractor.apkHelper.getInstalledApks()).filter(apkInfo -> apkInfo.getPackageName().equals(packageName)).findAny().orElse(null);
	}

	public void doWork(PackageCallback packageCallback) {
		if (unzipDefaultArchives()) {
			File destDir = new File(cacheDir, "result");
			try {
				ZipUtils.extractZip(new File(cacheDir, SOURCES_ZIP), destDir);
				ZipUtils.extractZip(new File(cacheDir, SUBSTRATUM_ZIP), destDir);
				ZipUtils.mergeDirectories(destDir, assetDirs.toArray(new File[assetDirs.size()]));

				if (apkExtractor == null) {
					apkExtractor = new ApkExtractor();
				}

				StreamSupport.stream(apkInfoList).forEach(apkInfo -> {
					if (apkInfo != null) {
						try {
							File apkFile = apkExtractor.copyApkToCache(cacheDir, apkInfo);
							if (apkFile != null && apkFile.exists()) {
								apkExtractor.extractAssetsFromApk(apkFile, destDir);
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					} else {
						Log.d(TAG, "Skipping ApkInfo, was null...");
					}
				});

				File apkFile = new File(cacheDir, "dummy.apk");
				File signedApk = ZipUtils.createApkFromDir(destDir, apkFile);
				if (signedApk.exists()) {
					packageCallback.onPackagingSucceeded(signedApk);
				} else {
					packageCallback.onPackagingFailed(-1);
				}
			} catch (Exception e) {
				e.printStackTrace();
				//TODO: implement proper error handling
				packageCallback.onPackagingFailed(0);
			}
		} else {
			packageCallback.onPackagingFailed(1);
		}
	}

	public void cleanCache() {
		try {
			FileUtils.cleanDirectory(cacheDir);
			Log.i(TAG, "Cache directory cleaned at " + cacheDir.getAbsolutePath());
		} catch (IOException io) {
			io.printStackTrace();
			Log.e(TAG, "Unable to clean cache directory!");
		}
	}

	public void processPackageRequest(PackageRequest packageRequest, PackageCallback packageCallback) {
		/*File tempDir = new File(cacheDir, "tmp_build_" + System.nanoTime());
		boolean createTempDir = tempDir.mkdirs();
		if(!createTempDir && !tempDir.exists()) {
			Log.e(TAG, "Couldn't create temporary directory to package patched application!");
			packageCallback.onPackagingFailed(-99);
			return;
		}*/
		if (unzipDefaultArchives()) {
			File destDir = new File(cacheDir, "result");
			try {
				ZipUtils.extractZip(new File(cacheDir, SOURCES_ZIP), destDir);
				ZipUtils.extractZip(new File(cacheDir, SUBSTRATUM_ZIP), destDir);

				if (apkExtractor == null) {
					apkExtractor = new ApkExtractor();
				}

				File cachedAssetsDir = new File(destDir, ASSETS_DIR);
				packageRequest.copyResourcesIntoDir(cachedAssetsDir);

				File apkFile = new File(cacheDir, "dummy.apk");
				File signedApk = ZipUtils.createApkFromDir(destDir, apkFile);
				if (signedApk.exists()) {
					packageCallback.onPackagingSucceeded(signedApk);
				} else {
					packageCallback.onPackagingFailed(-1);
				}
			} catch (Exception e) {
				e.printStackTrace();
				//TODO: implement proper error handling
				packageCallback.onPackagingFailed(0);
			}
		} else {
			packageCallback.onPackagingFailed(1);
		}

	}

	public static final class Builder {

		private final File          cacheDir;
		private       Context       context;
		private       List<File>    assetDirs;
		private       List<ApkInfo> apkInformationList;

		public Builder(Context context) {
			this.cacheDir = context.getCacheDir();
			this.context = context.getApplicationContext();
			this.assetDirs = new ArrayList<>();
			this.apkInformationList = new ArrayList<>();
		}

		public Builder addAssetsDir(File dir) {
			if (dir != null && dir.isDirectory()) {
				this.assetDirs.add(dir);
			}
			return this;
		}

		public Builder addApkInfo(ApkInfo apkInfo) {
			if (apkInfo != null) {
				apkInformationList.add(apkInfo);
			}
			return this;
		}

		public SubstratumPackager build() {
			return new SubstratumPackager().applyConfig(this);
		}

	}

	public static final class PackageRequest {

		private final Map<AssetsType, List<AssetFileInfo>> requestMap;

		public PackageRequest() {
			this.requestMap = new HashMap<>();
		}

		public void setFontSources(List<AssetFileInfo> fontSources) {
			this.requestMap.put(AssetsType.FONTS, fontSources);
		}

		public void setOverlaySources(List<AssetFileInfo> overlaySources) {
			this.requestMap.put(AssetsType.OVERLAYS, overlaySources);
		}

		public void setAudioSources(List<AssetFileInfo> audioSources) {
			this.requestMap.put(AssetsType.AUDIO, audioSources);
		}

		public void setBootAnimationSources(List<AssetFileInfo> bootAnimationSources) {
			this.requestMap.put(AssetsType.BOOT_ANIMATIONS, bootAnimationSources);
		}

		private void copyResourcesIntoDir(File cacheAssetsRoot) {
			StreamSupport.stream(Collections.synchronizedSet(requestMap.keySet())).forEachOrdered(keySet -> {
				List<AssetFileInfo> files = Collections.synchronizedList(requestMap.get(keySet));
				StreamSupport.stream(files).forEachOrdered(assetFileInfo -> {
					File target = new File(assetFileInfo.getFileLocation());
					File destDir = cacheAssetsRoot;

					switch (keySet) {
						case AUDIO:
							destDir = new File(cacheAssetsRoot, AUDIO_DIR);
							break;
						case BOOT_ANIMATIONS:
							destDir = new File(cacheAssetsRoot, BOOT_ANIMATION_DIR);
							break;
						case FONTS:
							destDir = new File(cacheAssetsRoot, FONTS_DIR);
							break;
						case OVERLAYS:
							destDir = new File(cacheAssetsRoot, OVERLAYS_DIR);
							break;
					}

					if(assetFileInfo.getRelativeAssetsDestinationLocation() != null) {
						destDir = new File(cacheAssetsRoot, assetFileInfo.getRelativeAssetsDestinationLocation());
					}

					if (!destDir.exists()) {
						destDir.mkdirs();
					}

					try {
						if (target.isDirectory()) {
							FileUtils.copyDirectory(target, destDir);
						} else {
							FileUtils.copyFileToDirectory(target, destDir);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
			});
		}
	}

}
