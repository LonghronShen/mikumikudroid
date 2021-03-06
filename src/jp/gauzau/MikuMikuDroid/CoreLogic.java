package jp.gauzau.MikuMikuDroid;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.Matrix;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.KeyEvent;

public class CoreLogic {
	// model / music data
	private ArrayList<Miku> mMiku;
	private String mBG;
	private MikuMotion mCamera;
	private MediaPlayer mMedia;
	private FakeMedia mFakeMedia;
	private String mMediaName;
	private long mCurTime;
	private long mPrevTime;
	private long mStartTime;
	private double mFPS;

	private float[] mPMatrix = new float[16];
	private float[] mMVMatrix = new float[16];
	private float[] mRMatrix = new float[16];
	volatile private float[] mCameraOrientation = new float[3];
	volatile private float[] cameraPosition = new float[3];
	public float cameraOffsetHeight = 0;
	public float cameraOffsetDist = 0;
	float cameraDistance = 13f;
	private float sensorRotationMatrix[] = null;
	
	// configurations
	private String mBase;
	private String mInternalDataDir;
	private int mMaxBone = 0;
	private Context mCtx;
	private int mScreenWidth;
	private int mScreenHeight;
	private int mRenderWidth;
	private int mRenderHeight;
	private int mAngle;
	private boolean mPhysics = true;
	private boolean mPrevPhysics = false;
	
	public boolean cameraVisible = false;
	
	public static final int CAMERA_MODE_FIXED = 0;
	public static final int CAMERA_MODE_MOTION = 1;
	public static final int CAMERA_MODE_SENSOR = 2;
	public static final int CAMERA_MODE_SENSOR2 = 3;
	public static final int CAMERA_MODE_MAX = 4;
	private int cameraMode = CAMERA_MODE_SENSOR;
	
	public volatile boolean enableOculusMode = false;

	public boolean keyState[] = new boolean[256];
	public float analogInput[] = new float[16];

	
	// temporary data
	private CameraIndex mCameraIndex = new CameraIndex();
	private CameraPair mCameraPair = new CameraPair();

	private class FakeMedia {
		private WakeLock mWakeLock;
		private boolean mIsPlaying;
		private long mCallTime;
		private int mPos;
		private boolean mIsFinished;
		private int mMax;

		public FakeMedia(Context ctx) {
			PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "MikuMikuDroid");
			mIsPlaying = false;
			mIsFinished = false;
			mPos = 0;
			mMax = 0;
		}

		public boolean toggleStartStop() {
			updatePos();
			if (mIsPlaying) { // during play
				stop();
			} else {
				start();
			}
			return mIsPlaying;
		}

		public int getCurrentPosition() {
			updatePos();

			return mPos;
		}

		public void relaseLock() {
			if (mIsPlaying) {
				stop();
			}
			mIsFinished = false;
			mPos = 0;
			mMax = 0;
		}

		private void updatePos() {
			if (mIsPlaying) {
				long cur = System.currentTimeMillis();
				mPos += (cur - mCallTime);
				mCallTime = cur;

				if (mPos > mMax) {
					mPos = mMax;
					mIsFinished = true;
					stop();
				}
			}
		}

		private void start() {
			mWakeLock.acquire();
			mIsPlaying = true;
			mCallTime = System.currentTimeMillis();
			if (mIsFinished) {
				mIsFinished = false;
				mPos = 0;
			}
		}

		private void stop() {
			mWakeLock.release();
			mIsPlaying = false;
		}

		public void seekTo(int i) {
			mPos = i;
		}

		public void pause() {
			if (mIsPlaying) {
				stop();
			}
		}

		public void setMax(int maxFrame) {
			mMax = maxFrame * 1000 / 30;
		}

		public int getDuration() {
			return mMax;
		}

		public boolean isPlaying() {
			return mIsPlaying;
		}

	}

	native private void btMakeWorld();

	native private void btDumpAll();

	static {
		if (isArm()) {
			System.loadLibrary("bullet-jni");
			Log.d("Miku", "Use ARM native codes.");
		}
	}

	public CoreLogic(Context ctx) {
		mCtx = ctx;
		mFakeMedia = new FakeMedia(ctx);

		clearMember();
		if (isArm()) {
			btMakeWorld();
		}
		

		if (new File("/sdcard/.MikuMikuDroid").exists()) {
			mBase = "/sdcard/.MikuMikuDroid/";
		} else {
			mBase = "/sdcard/MikuMikuDroid/";
		}

		
		File f = new File(ctx.getFilesDir(), "/Data/toon0.bmp");
		Log.d("MikuMikuDroid", f.getAbsolutePath() + ":" + f.isFile());
		if (!f.isFile()) {
			String srcDir = "toon";
			String dstDir = ctx.getFilesDir().getAbsolutePath() + "/Data";
			try {
				String files[] = ctx.getAssets().list(srcDir);
				new File(dstDir).mkdirs();
				for (String file : files) {
					InputStream is = null;
					OutputStream os = null;
					try {
						is = ctx.getAssets().open(srcDir + "/" + file);
						os = new FileOutputStream(dstDir + "/" + file);
						byte[] buf = new byte[1024];
						int length;
						while ((length = is.read(buf)) > 0) {
							os.write(buf, 0, length);
						}
					} finally {
						if (is != null)
							is.close();
						if (os != null)
							os.close();
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
		Log.d("MikuMikuDroid", f.getAbsolutePath() + ":" + f.isFile());
		
		mInternalDataDir = ctx.getFilesDir().getAbsolutePath() + "/";
		
	}

	public void setGLConfig(int max_bone) {
		if (mMaxBone == 0) {
			mMaxBone = max_bone;
			onInitialize();
		}
	}

	public void onInitialize() {
	}

	// ///////////////////////////////////////////////////////////
	// Model configurations
	public void loadModel(String modelf) throws IOException {
		// model
		PMDParser pmd = new PMDParser(mInternalDataDir, modelf);
		MikuModel model = new MikuModel(mBase, pmd, mMaxBone, false);

		// Create Miku
		Miku miku = new Miku(model);

		// add Miku
		mMiku.add(miku);
	}

	public void loadMotion(String motionf) throws IOException {
		// motion
		VMDParser vmd = new VMDParser(motionf);
		MikuMotion motion = null;

		// check IK cache
		String vmc = motionf.replaceFirst(".vmd", "_mmcache.vmc");
		try {
			ObjectInputStream oi = new ObjectInputStream(new FileInputStream(vmc));
			motion = (MikuMotion) oi.readObject();
			motion.attachVMD(vmd);
			oi.close();
		} catch (Exception e) {
			motion = new MikuMotion(vmd);
		}

		// Create Miku
		if (mMiku != null) {
			Miku miku = mMiku.get(mMiku.size() - 1);
			miku.attachMotion(motion);
			miku.setBonePosByVMDFramePre(0, 0, true);
			miku.setBonePosByVMDFramePost(mPhysics);
			miku.setFaceByVMDFrame(0);

			// store IK chache
			File f = new File(vmc);
			if (!f.exists()) {
				ObjectOutputStream oi = new ObjectOutputStream(new FileOutputStream(vmc));
				oi.writeObject(motion);
				oi.close();
			}
		}
	}

	public synchronized boolean loadModelMotion(String modelf, String motionf) throws IOException, OutOfMemoryError {
		// read model/motion files
		ModelFile modelFile = null;
		if (modelf.endsWith(".pmx")) {
			Log.d("loadModelMotion","PMXParser");
			PMXParser pmx = new PMXParser(mInternalDataDir, modelf);
			if (pmx.isPmd()) {
				Log.d("loadModelMotion","isPMX OK");
				modelFile = pmx;
			}
			modelFile = pmx;
		} else {
			PMDParser pmd = new PMDParser(mInternalDataDir, modelf);
			if (pmd.isPmd()) {
				modelFile = pmd;
			}
		}

		Log.d("loadModelMotion","isPmd");
		if (modelFile != null) {
			// create texture cache
			Log.d("loadModelMotion","pmd");
			createTextureCache(modelFile);

			// construct model/motion data structure
			MikuModel model = new MikuModel(mBase, modelFile, mMaxBone, true);
			MikuMotion motion = null;
			modelFile = null;

			// delete previous cache
			String vmc = motionf.replaceFirst(".vmd", "_mmcache.vmc");
			File vmcf = new File(vmc);
			if (vmcf.exists()) {
				vmcf.delete();
			}

			VMDParser vmd = new VMDParser(motionf);
			if (vmd.isVmd()) {
				Log.d("loadModelMotion","vmd");
				// check IK cache
				CacheFile c = new CacheFile(mBase, "vmc");
				c.addFile(modelf);
				c.addFile(motionf);
				vmc = c.getCacheFileName();
				boolean vmc_success = true;
				Log.d("loadModelMotion","try");
				try {
					ObjectInputStream oi = new ObjectInputStream(new FileInputStream(vmc));
					motion = (MikuMotion) oi.readObject();
					motion.attachVMD(vmd);
					oi.close();
				} catch (Exception e) {
					Log.d("loadModelMotion","fail");
					motion = new MikuMotion(vmd);
					vmc_success = false;
				}
				vmd = null;

				// Create Miku
				Miku miku = new Miku(model);
				miku.attachMotion(motion);
				miku.setBonePosByVMDFramePre(0, 0, true);
				miku.setBonePosByVMDFramePost(mPhysics);
				miku.setFaceByVMDFrame(0);
				miku.addRenderSenario("builtin:default", "screen");
				miku.addRenderSenario("builtin:default_alpha", "screen");

				// store IK chache
				if (!vmc_success) {
					File f = new File(vmc);
					if (!f.exists()) {
						f.delete();
					}
					ObjectOutputStream oi = new ObjectOutputStream(new FileOutputStream(vmc));
					oi.writeObject(motion);
				}

				// add Miku
				mMiku.add(miku);

				// set max dulation
				mFakeMedia.setMax(motion.maxFrame());

				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	public synchronized boolean loadAccessory(String modelf) throws IOException, OutOfMemoryError {
		// create cache
		CacheFile c = new CacheFile(mBase, "xc");
		c.addFile(modelf);
		String xc = c.getCacheFileName();
		ModelBuilder mb = new ModelBuilder(modelf);

		if (!c.hasCache() || !mb.readFromFile(xc)) {
			XParser x = new XParser(mInternalDataDir, modelf, 10.0f, mBase);
			if (x.isX()) {
				x.getModelBuilder().writeToFile(xc);
			}
			if (!mb.readFromFile(xc)) {
				return false;
			}
		}

		createTextureCache(mb);
		MikuModel model = new MikuModel(mBase, mb, mMaxBone, false);
		Miku miku = new Miku(model);
		miku.addRenderSenario("builtin:nomotion", "screen");
		miku.addRenderSenario("builtin:nomotion_alpha", "screen");
		mMiku.add(miku);
		return true;
	}

	public synchronized MikuModel loadStage(String file) throws IOException, OutOfMemoryError {
		ModelFile modelFile = null;
		if (file.endsWith(".pmx")) {
			Log.d("loadModelMotion","PMXParser");
			PMXParser pmx = new PMXParser(mInternalDataDir, file);
			if (pmx.isPmd()) {
				Log.d("loadModelMotion","isPMX OK");
				modelFile = pmx;
			} else {
				modelFile = null;
			}
		} else {
			PMDParser pmd = new PMDParser(mInternalDataDir, file);
			if (pmd.isPmd()) {
				modelFile = pmd;
			} else {
				modelFile = null;
			}
		}

		if (modelFile != null) {
			createTextureCache(modelFile);
			MikuModel model = new MikuModel(mBase, modelFile, mMaxBone, false);
			Miku miku = new Miku(model);
			miku.addRenderSenario("builtin:nomotion", "screen");
			miku.addRenderSenario("builtin:nomotion_alpha", "screen");
			mMiku.add(miku);
		}
		return null;
	}

	public synchronized String loadBG(String file) {
		String tmp = mBG;
		mBG = file;
		return tmp;
	}

	public double getFPS() {
		return mFPS;
	}

	public synchronized void loadMedia(String media) {
		if (mMedia != null) {
			mMedia.stop();
			mMedia.release();
		} else {
			mFakeMedia.relaseLock();
		}

		mMediaName = media;
		Uri uri = Uri.parse(media);
		mMedia = MediaPlayer.create(mCtx, uri);
		if (mMedia != null) {
			mMedia.setWakeMode(mCtx, PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE);
			mMedia.setLooping(true);
		}
	}

	public synchronized void loadCamera(String camera) throws IOException {
		mCamera = new MikuMotion(new VMDParser(camera));
		cameraMode = CAMERA_MODE_MOTION;
	}

	public synchronized void togglePhysics() {
		mPhysics = !mPhysics;
		if (!mPhysics) { // Dump Physics profile
			btDumpAll();
		}
	}

	native private void btClearAllData();

	public synchronized ArrayList<MikuModel> clear() {
		// get members for deleting textures
		ArrayList<MikuModel> models = new ArrayList<MikuModel>();
		for (Miku m : mMiku) {
			models.add(m.mModel);
		}

		clearMember();

		// clear physics world
		btClearAllData();

		SharedPreferences sp = mCtx.getSharedPreferences("default", 0);
		SharedPreferences.Editor ed = sp.edit();
		ed.clear();
		ed.commit();

		return models;
	}

	private void clearMember() {
		mMiku = new ArrayList<Miku>();
		mPrevTime = 0;
		mStartTime = 0;
		mCamera = null;
		if (mMedia != null) {
			mMedia.stop();
			mMedia.release();
		} else {
			mFakeMedia.relaseLock();
		}
		mMedia = null;
		mAngle = 0;
		mBG = null;

		setDefaultCamera();
	}

	public synchronized void setScreenAngle(int angle) {
		mAngle = angle;
		if (mCamera == null) {
			setDefaultCamera();
		}
	}

	native private void btStepSimulation(float step, int max);

	public synchronized int applyCurrentMotion() {
		boolean initializePhysics = !mPrevPhysics && mPhysics;
		mPrevPhysics = mPhysics;

		checkKeyState();
		
		calcCurrentTime();
		double frame = getCurrentFrames(32767);
		float step;
		if (isPlaying()) {
			step = (float) (getDeltaTimeMills() / 1000.0);
		} else {
			step = 10 / 1000.0f;
		}
		if (step >= 1 || step < 0) {
			step = 0;
			initializePhysics = true;
		}

		if (initializePhysics) {
			btClearAllData();
		}

		double prev_frame = calcPrevFrame(frame, step);

		int max_step = 4;
		float fixed_delta = (float) (1.0 / 60.0);
		float delta = Math.max(fixed_delta, step / max_step);

		// exec physics simulation: 60Hz
		if (isArm() && mPhysics && mMiku != null && (step != 0 || !initializePhysics)) {
			for (; step > 0.00001; step -= delta) {
				prev_frame += step > delta ? delta * 30 : step * 30;
				for (Miku miku : mMiku) {
					if (miku.hasMotion()) {
						miku.setBonePosByVMDFramePre((float) prev_frame, delta, false);
					}
				}
				if (delta == fixed_delta) {
					btStepSimulation(delta, 1);
				} else {
					btStepSimulation(delta, 0);
				}
				for (Miku miku : mMiku) {
					if (miku.hasMotion()) {
						for (Bone b : miku.mModel.mBone) {
							b.updated = false;
						}
					}
				}
			}
		}

		if (mMiku != null) {
			for (Miku miku : mMiku) {
				if (miku.hasMotion()) {
					miku.setBonePosByVMDFramePre((float) frame, 0, initializePhysics);
					miku.setBonePosByVMDFramePost(mPhysics);
					miku.setFaceByVMDFrame((float) frame);
				}
			}
		}

		setCameraByVMDFrame(frame, 0);

		return (int) (frame * 1000 / 30);
	}

	public void moveCameraX(float x) {
		double frame = getCurrentFrames(32767);
		setCameraByVMDFrame(frame, x);
	}

	public void setCameraOrientation(float[] orientation) {
		mCameraOrientation[0] = orientation[0];
		mCameraOrientation[1] = orientation[1];
		mCameraOrientation[2] = orientation[2];
	}

	public void setSensorRotationMatrix(float[] mat) {
		sensorRotationMatrix = mat;
	}

	public float[] getCameraOrientationAsRef() {
		return mCameraOrientation;
	}

	public void setCameraPosition(float[] position) {
		cameraPosition[0] = position[0]; // X
		cameraPosition[1] = position[1]; // Y
		cameraPosition[2] = position[2]; // Z
	}

	public float[] getCameraPositionAsRef() {
		return cameraPosition;
	}

	
	public void toggleViewMode() {
		cameraMode = (cameraMode + 1) % CAMERA_MODE_MAX;
		if (cameraMode == CAMERA_MODE_SENSOR2) {
			cameraPosition[0] = 0;
			cameraPosition[1] = 18;
			cameraPosition[2] = 0;
			cameraDistance = 13f;
		} else {
			cameraPosition[0] = 0;
			cameraPosition[1] = 18;
			cameraPosition[2] = -13;
			cameraDistance = 0f;
		}
		
	}

	public int getCameraMode() {
		return cameraMode;
	}

	public void toggleCameraView() {
		cameraVisible = !cameraVisible;
	}

	private double calcPrevFrame(double frame, float step) {
		return frame - step * 30;
	}

	public void pause() {
		if (mMedia != null) {
			mMedia.pause();
		} else {
			mFakeMedia.pause();
		}
	}

	public boolean toggleStartStop() {
		if (mMedia != null) {
			if (mMedia.isPlaying()) {
				mMedia.pause();
				return false;
			} else {
				mMedia.start();
				return true;
			}
		} else {
			return mFakeMedia.toggleStartStop();
		}
	}

	public boolean isPlaying() {
		if (mMedia != null) {
			return mMedia.isPlaying();
		} else {
			return mFakeMedia.isPlaying();
		}
	}

	public void rewind() {
		seekTo(0);
	}

	public void seekTo(int pos) {
		if (mMedia != null) {
			mMedia.seekTo(pos);
		} else {
			mFakeMedia.seekTo(pos);
		}
	}

	public int getDulation() {
		if (mMedia != null) {
			return mMedia.getDuration();
		} else {
			return mFakeMedia.getDuration();
		}
	}

	public float[] getRotationMatrix() {
		return mRMatrix;
	}

	public void onDraw(final int pos) {
	}

	public void storeState() {
		SharedPreferences sp = mCtx.getSharedPreferences("default", Context.MODE_PRIVATE);
		SharedPreferences.Editor ed = sp.edit();

		// model & motion
		if (mMiku != null) {
			ed.putInt("ModelNum", mMiku.size());
			for (int i = 0; i < mMiku.size(); i++) {
				Miku m = mMiku.get(i);
				ed.putString(String.format("Model%d", i), m.mModel.mFileName);
				if (m.mMotion != null) {
					ed.putString(String.format("Motion%d", i), m.mMotion.mFileName);
				}
			}
		} else {
			ed.putInt("ModelNum", 0);
		}

		// camera
		if (mCamera != null) {
			ed.putString("Camera", mCamera.mFileName);
		}

		// music
		if (mMedia != null) {
			ed.putString("Music", mMediaName);
			int cur = mMedia.getCurrentPosition();
			if (cur + 100 < mMedia.getDuration()) {
				ed.putInt("Position", cur);
			}
		} else {
			int cur = mFakeMedia.getCurrentPosition();
			if (cur + 100 < mFakeMedia.getDuration()) {
				ed.putInt("Position", cur);
			}
		}

		ed.commit();
		Log.d("CoreLogic", "Store State");
	}

	public void restoreState() {
		SharedPreferences sp = mCtx.getSharedPreferences("default", Context.MODE_PRIVATE);

		try {
			// model & motion
			int num = sp.getInt("ModelNum", 0);

			String model[] = new String[num];
			String motion[] = new String[num];
			for (int i = 0; i < num; i++) {
				model[i] = sp.getString(String.format("Model%d", i), null);
				motion[i] = sp.getString(String.format("Motion%d", i), null);
			}

			String camera = sp.getString("Camera", null);
			String music = sp.getString("Music", null);
			int pos = sp.getInt("Position", 0);

			// crear preferences
			Editor ed = sp.edit();
			ed.clear();
			ed.commit();

			// load data
			for (int i = 0; i < num; i++) {
				if (motion[i] == null) {
					if (model[i].endsWith(".x")) {
						loadAccessory(model[i]);
					} else {
						loadStage(model[i]);
					}
				} else {
					loadModelMotion(model[i], motion[i]);
				}
			}

			if (camera != null) {
				loadCamera(camera);
			}

			if (music != null) {
				loadMedia(music);
				if (mMedia != null) {
					mMedia.seekTo(pos);
				}
			} else {
				mFakeMedia.seekTo(pos);
			}

			// restore
			storeState();

		} catch (IOException e) {
			Editor ed = sp.edit();
			ed.clear();
			ed.commit();
		}
	}

	public void setScreenSize(int width, int height) {
		mScreenWidth = width;
		mScreenHeight = height;
		mRenderWidth = width;
		mRenderHeight = height;
	}

	public void setRenderSize(int width, int height) {
		mRenderWidth = width;
		mRenderHeight = height;
	}

	public int getScreenWidth() {
		return mScreenWidth;
	}

	public int getScreenHeight() {
		return mScreenHeight;
	}

	public ArrayList<Miku> getMiku() {
		return mMiku;
	}

	public void returnMiku(Miku miku) {

	}

	public String getBG() {
		return mBG;
	}

	public void returnMikuStage(Miku stage) {

	}

	public float[] getProjectionMatrix() {
		return mPMatrix;
	}

	public boolean checkFileIsPrepared() {
		File files = new File(mInternalDataDir + "Data/toon0.bmp");
		return files.exists();
	}

	public File[] getModelSelector() {
		String[] ext = { ".bmp", ".jpg", ".png", ".tga" };

		String[] extm = { ".pmd", ".x", ".pmx" };

		File[] model = listFiles(mBase + "", extm); // UserFile/Model/
		File[] bg = listFiles(mBase + "UserFile/BackGround/", ext);
		//File[] acc = listFiles(mBase + "UserFile/Accessory/", ".x");
		File[] f = new File[model.length + bg.length]; //   + acc.length
		for (int i = 0; i < model.length; i++) {
			f[i] = model[i];
		}
		for (int i = 0; i < bg.length; i++) {
			f[i + model.length] = bg[i];
		}
		//for (int i = 0; i < acc.length; i++) {
		//	f[i + model.length + bg.length] = acc[i];
		//}
		return f;
	}

	public File[] getMotionSelector() {
		return listFiles(mBase + "", ".vmd"); // UserFile/Motion/
	}

	public File[] getCameraSelector() {
		return listFiles(mBase + "", ".vmd"); // UserFile/Motion/
	}

	public File[] getMediaSelector() {
		String[] ext = { ".mp3", ".wav", ".3gp", ".mp4", ".m4a", ".ogg" };
		return listFiles(mBase + "", ext); // UserFile/Wave/
	}

	public File[] listFiles(String dir, String ext) {
		String[] exts = new String[1];
		exts[0] = ext;
		return listFiles(dir, exts);
	}

	public File[] listFiles(String dir, String[] ext) {
		File file = new File(dir);
		ArrayList<File> list = listRecursive1(file, ext);
		return (File[]) list.toArray(new File[0]);
	}

	private ArrayList<File> listRecursive1(File file, String[] ext) {
		ArrayList<File> files = new ArrayList<File>();
		if (file.exists()) {
			if (file.isFile()) {
				for (int i = 0; i < ext.length; i++) {
					if (file.getName().endsWith(ext[i])) {
						files.add(file);
						break;
					}
				}
			} else {
				File[] list = file.listFiles();
				for (int i = 0; i < list.length; i++) {
					files.addAll(listRecursive1(list[i], ext));
				}
			}
		}

		return files;
	}

	public String getRawResourceString(int id) {
		char[] buf = new char[1024];
		StringWriter sw = new StringWriter();

		BufferedReader is = new BufferedReader(new InputStreamReader(mCtx.getResources().openRawResource(id)));
		int n;
		try {
			while ((n = is.read(buf)) != -1) {
				sw.write(buf, 0, n);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		return sw.toString();
	}

	public void logMemoryUsage() {
		Runtime runtime = Runtime.getRuntime();
		Log.d("CoreLogic", "totalMemory[KB] = " + (int) (runtime.totalMemory() / 1024));
		Log.d("CoreLogic", "freeMemory[KB] = " + (int) (runtime.freeMemory() / 1024));
		Log.d("CoreLogic", "usedMemory[KB] = " + (int) ((runtime.totalMemory() - runtime.freeMemory()) / 1024));
		Log.d("CoreLogic", "maxMemory[KB] = " + (int) (runtime.maxMemory() / 1024));
	}

	static boolean isArm() {
		return Build.CPU_ABI.contains("armeabi");
	}

	// ///////////////////////////////////////////////////////////
	// Some common methods

	private void createTextureCache(ModelFile pmd) {
		// create texture cache
		for (Material mat : pmd.getMaterial()) {
			if (mat.texture != null) {
				TextureFile.createCache(mBase, mat.texture, 1);
			}
			if (mat.sphere != null) {
				TextureFile.createCache(mBase, mat.sphere, 1);
			}
		}
	}

	private void createTextureCache(ModelBuilder pmd) {
		// create texture cache
		for (Material mat : pmd.mMaterial) {
			if (mat.texture != null) {
				TextureFile.createCache(mBase, mat.texture, 1);
			}
			if (mat.sphere != null) {
				TextureFile.createCache(mBase, mat.sphere, 1);
			}
		}
	}

	private void calcCurrentTime() {
		// update previous time
		mPrevTime = mCurTime;

		// calculate current time
		if (mMedia != null) {
			mCurTime = mMedia.getCurrentPosition();
			if (mMedia.isPlaying()) {
				long timeLocal = System.currentTimeMillis();
				if (Math.abs(timeLocal - mStartTime - mCurTime) > 500 || mMedia.isPlaying() == false) {
					mStartTime = timeLocal - mCurTime;
				} else {
					mCurTime = timeLocal - mStartTime;
				}
			}
		} else {
			mCurTime = mFakeMedia.getCurrentPosition();
		}
	}

	protected double getCurrentFrames(int max_frame) {
		double frame;
		frame = ((float) mCurTime * 30.0 / 1000.0);
		if (frame > max_frame) {
			frame = max_frame;
		}
		mFPS = 1000.0 / (mCurTime - mPrevTime);

		return frame;
	}

	private long getDeltaTimeMills() {
		return mCurTime - mPrevTime;
	}

	protected void setCameraByVMDFrame(double frame, float dx) {
		if (cameraMode == CAMERA_MODE_MOTION && mCamera != null) {
			CameraPair cp = mCamera.findCamera((float) frame, mCameraPair);
			CameraIndex c = mCamera.interpolateLinear(cp, (float) frame, mCameraIndex);
			if (c != null) {
				setCamera(c.length * 0.8f - 2f, dx, c.location, c.rotation, c.view_angle, mRenderWidth, mRenderHeight);
			}
		} else {
			if (mAngle == 0) {
				mCameraIndex.location[0] = cameraPosition[0];
				mCameraIndex.location[1] = cameraPosition[1] + cameraOffsetHeight;
				mCameraIndex.location[2] = cameraPosition[2] + cameraOffsetDist;
				mCameraIndex.rotation[0] = -mCameraOrientation[2] * 180 / 3.14159f;
				mCameraIndex.rotation[1] = -mCameraOrientation[0] * 180 / 3.14159f;
				mCameraIndex.rotation[2] = -mCameraOrientation[1] * 180 / 3.14159f;
				setCamera(-cameraDistance, dx, mCameraIndex.location, mCameraIndex.rotation, 120, mRenderWidth, mRenderHeight); // -38f
			} else {
				mCameraIndex.location[0] = cameraPosition[0];
				mCameraIndex.location[1] = cameraPosition[1] + cameraOffsetHeight;
				mCameraIndex.location[2] = cameraPosition[2] + cameraOffsetDist;
				mCameraIndex.rotation[0] = -mCameraOrientation[2] * 180 / 3.14159f;
				mCameraIndex.rotation[1] = -mCameraOrientation[0] * 180 / 3.14159f;
				mCameraIndex.rotation[2] = -mCameraOrientation[1] * 180 / 3.14159f;
				setCamera(-10f, dx, mCameraIndex.location, mCameraIndex.rotation, 120, mRenderWidth, mRenderHeight);
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	protected void setCamera(float d, float dx, float[] pos, float[] rot, float angle, int width, int height) {
		// Projection Matrix
		float a = enableOculusMode ? 100f : 45f;
		float s = (float) Math.sin(angle * Math.PI / 360);
		Matrix.setIdentityM(mPMatrix, 0);
		if (mAngle == 90) {
			Matrix.frustumM(mPMatrix, 0, -s, s, -s * height / width, s * height / width, 1f, 3500f);
		} else {
			//Matrix.frustumM(mPMatrix, 0, -s * width / height, s * width / height, -s, s, 1f, 3500f);
			if (Matrix.length(pos[0], pos[1], pos[2]) < 20) {
				Matrix.perspectiveM(mPMatrix, 0, a, width * 1.0f / height,  0.5f, 300f);
			} else {
				Matrix.perspectiveM(mPMatrix, 0, a, width * 1.0f / height,  4f, 2000f);				
			}
		}
		Matrix.scaleM(mPMatrix, 0, 1, 1, -1); // to right-handed
		Matrix.rotateM(mPMatrix, 0, mAngle, 0, 0, -1); // rotation

		Matrix.multiplyMM(mPMatrix, 0, mPMatrix, 0, mRMatrix, 0); // device rotation

		// camera
		if (cameraMode == CAMERA_MODE_SENSOR) {
			Matrix.translateM(mPMatrix, 0, -dx, 0, 0.01f); // 
		} else {
			Matrix.translateM(mPMatrix, 0, -dx, 0, -d); // 
		}
		if (sensorRotationMatrix != null) {
			// FIXME!
			float result[] = new float[16];
			Matrix.multiplyMM(result, 0, mPMatrix, 0, sensorRotationMatrix, 0);
			mPMatrix = result;
		}
		Matrix.rotateM(mPMatrix, 0, rot[2], 0, 0, 1f);
		Matrix.rotateM(mPMatrix, 0, rot[0], 1f, 0, 0);
		Matrix.rotateM(mPMatrix, 0, rot[1], 0, 1f, 0);
		Matrix.translateM(mPMatrix, 0, -pos[0], -pos[1], -pos[2]);

		// model-view matrix (is null)
		Matrix.setIdentityM(mMVMatrix, 0);
	}

	protected void setDefaultCamera() {
		Matrix.setIdentityM(mRMatrix, 0);
		if (mAngle == 0) {
			mCameraIndex.location[0] = 0;
			mCameraIndex.location[1] = 10; // 13
			mCameraIndex.location[2] = 0;
			mCameraIndex.rotation[0] = 0;
			mCameraIndex.rotation[1] = 0;
			mCameraIndex.rotation[2] = 0;
			setCamera(-35f, 0, mCameraIndex.location, mCameraIndex.rotation, 45, mScreenWidth, mScreenHeight); // -38f
		} else {
			mCameraIndex.location[0] = 0;
			mCameraIndex.location[1] = 10;
			mCameraIndex.location[2] = 0;
			mCameraIndex.rotation[0] = 0;
			mCameraIndex.rotation[1] = 0;
			mCameraIndex.rotation[2] = 0;
			setCamera(-30f, 0, mCameraIndex.location, mCameraIndex.rotation, 45, mScreenWidth, mScreenHeight);
		}
	}

	
	private void checkKeyState() {
		float d = 0.5f;
		if (keyState[KeyEvent.KEYCODE_DPAD_UP]) {
			if (cameraMode == CAMERA_MODE_SENSOR) {
				cameraPosition[0] += Math.sin(mCameraOrientation[0]) * d;
				cameraPosition[2] += Math.cos(mCameraOrientation[0]) * d;
			} else {
				cameraDistance -= d;
			}
		}
		if (keyState[KeyEvent.KEYCODE_DPAD_DOWN]) {
			if (cameraMode == CAMERA_MODE_SENSOR) {
				cameraPosition[0] -= Math.sin(mCameraOrientation[0]) * d;
				cameraPosition[2] -= Math.cos(mCameraOrientation[0]) * d;
			} else {
				cameraDistance += d;
			}
		}

		if (keyState[KeyEvent.KEYCODE_DPAD_LEFT]) {
			cameraPosition[0] -= Math.cos(mCameraOrientation[0]) * d;
			cameraPosition[2] += Math.sin(mCameraOrientation[0]) * d;
			cameraMode = CAMERA_MODE_SENSOR;
		}

		if (keyState[KeyEvent.KEYCODE_DPAD_RIGHT]) {
			cameraPosition[0] += Math.cos(mCameraOrientation[0]) * d;
			cameraPosition[2] -= Math.sin(mCameraOrientation[0]) * d;
			cameraMode = CAMERA_MODE_SENSOR;
		}
		if (analogInput[2] > 0.1 || analogInput[2] < -0.1) {
			// X
			cameraPosition[0] += Math.cos(mCameraOrientation[0]) * d * analogInput[2] * 2;
			cameraPosition[2] -= Math.sin(mCameraOrientation[0]) * d * analogInput[2] * 2;
		}

		if (analogInput[1] > 0.1 || analogInput[1] < -0.1) {
			// Y
			cameraPosition[0] -= Math.sin(mCameraOrientation[0]) * d * analogInput[1] * 2;
			cameraPosition[2] -= Math.cos(mCameraOrientation[0]) * d * analogInput[1] * 2;
		}

		if (analogInput[3] > 0.1 || analogInput[3] < -0.1) {
			cameraPosition[1] -= d * analogInput[3];
		}

		if (keyState[KeyEvent.KEYCODE_W]) {
			cameraPosition[1] += d*0.5f;
		}

		if (keyState[KeyEvent.KEYCODE_S]) {
			cameraPosition[1] -= d*0.5f;
		}
		
	}

}
