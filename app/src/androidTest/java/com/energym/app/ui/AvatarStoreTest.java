package com.energym.app.ui;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.Assert.*;

/** Fotos locales con archivos y propietarios UUID propios; no abre sesión ni modifica cuentas. */
public final class AvatarStoreTest {
	private File fixture;
	private File files;
	private AvatarStore store;
	private String owner;

	@Before public void setUp() {
		Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
		fixture = new File(base.getCacheDir(), "avatar-test-" + UUID.randomUUID());
		assertTrue(fixture.mkdirs());
		files = new File(fixture, "files");
		owner = "fixture-owner-" + UUID.randomUUID();
		store = new AvatarStore(new ContextWrapper(base) {
			@Override public Context getApplicationContext() { return this; }
			@Override public File getFilesDir() { return files; }
		});
	}

	@After public void tearDown() {
		// La raíz se creó para este test; nunca se recorre el directorio de datos real.
		deleteFixture(fixture);
	}

	@Test public void saveCentersTheCropAndWritesA320PixelJpeg() throws Exception {
		Bitmap source = Bitmap.createBitmap(960, 320, Bitmap.Config.ARGB_8888);
		try {
			Canvas canvas = new Canvas(source);
			Paint paint = new Paint();
			paint.setColor(Color.RED);
			canvas.drawRect(0, 0, 320, 320, paint);
			paint.setColor(Color.GREEN);
			canvas.drawRect(320, 0, 640, 320, paint);
			paint.setColor(Color.BLUE);
			canvas.drawRect(640, 0, 960, 320, paint);
			store.save(owner, Uri.fromFile(writeImage(source, "crop.png", Bitmap.CompressFormat.PNG)));
		} finally {
			source.recycle();
		}
		Bitmap avatar = store.load(owner);
		try {
			assertNotNull(avatar);
			assertEquals(320, avatar.getWidth());
			assertEquals(320, avatar.getHeight());
			assertColor(Color.GREEN, avatar.getPixel(16, 16));
			assertColor(Color.GREEN, avatar.getPixel(304, 304));
		} finally {
			if (avatar != null) avatar.recycle();
		}
		File[] avatars = new File(files, "avatars").listFiles();
		assertNotNull(avatars);
		assertEquals(1, avatars.length);
		assertTrue(avatars[0].getName().matches("[0-9a-f]{32}\\.jpg"));
		byte[] encoded = Files.readAllBytes(avatars[0].toPath());
		assertEquals(0xff, encoded[0] & 255);
		assertEquals(0xd8, encoded[1] & 255);
	}

	@Test public void saveAppliesAllEightExifOrientations() throws Exception {
		int[] orientations = { 1, 2, 3, 4, 5, 6, 7, 8 };
		int[][] expected = {
			{ Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW },
			{ Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE },
			{ Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED },
			{ Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN },
			{ Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW },
			{ Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN },
			{ Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED },
			{ Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE }
		};
		Bitmap source = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888);
		try {
			Canvas canvas = new Canvas(source);
			Paint paint = new Paint();
			int[] colors = { Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW };
			for (int i = 0; i < colors.length; i++) {
				paint.setColor(colors[i]);
				int x = (i % 2) * 320;
				int y = (i / 2) * 320;
				canvas.drawRect(x, y, x + 320, y + 320, paint);
			}
			for (int i = 0; i < orientations.length; i++) {
				File image = writeImage(source, "orientation-" + i + ".jpg", Bitmap.CompressFormat.JPEG);
				ExifInterface exif = new ExifInterface(image.getPath());
				exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(orientations[i]));
				exif.saveAttributes();
				store.save(owner, Uri.fromFile(image));
				Bitmap avatar = store.load(owner);
				try {
					assertNotNull(avatar);
					for (int quadrant = 0; quadrant < 4; quadrant++)
						assertColor(expected[i][quadrant], avatar.getPixel(
							80 + (quadrant % 2) * 160, 80 + (quadrant / 2) * 160));
				} finally {
					if (avatar != null) avatar.recycle();
				}
			}
		} finally {
			source.recycle();
		}
	}

	@Test public void failedSaveKeepsThePreviousPhotoAndOtherOwnersAreIndependent() throws Exception {
		String other = "fixture-owner-" + UUID.randomUUID();
		Bitmap source = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
		try {
			source.eraseColor(Color.GREEN);
			store.save(owner, Uri.fromFile(writeImage(source, "green.png", Bitmap.CompressFormat.PNG)));
			source.eraseColor(Color.BLUE);
			store.save(other, Uri.fromFile(writeImage(source, "blue.png", Bitmap.CompressFormat.PNG)));
		} finally {
			source.recycle();
		}
		File invalid = new File(fixture, "invalid.jpg");
		try (FileOutputStream out = new FileOutputStream(invalid)) { out.write(new byte[]{ 1, 2, 3 }); }
		try {
			store.save(owner, Uri.fromFile(invalid));
			fail("Una imagen inválida debe rechazarse");
		} catch (Exception expected) {
			assertTrue(store.has(owner));
		}
		Bitmap previous = store.load(owner);
		try {
			assertNotNull(previous);
			assertColor(Color.GREEN, previous.getPixel(160, 160));
		} finally {
			if (previous != null) previous.recycle();
		}
		store.delete(owner);
		assertFalse(store.has(owner));
		Bitmap otherPhoto = store.load(other);
		try {
			assertNotNull(otherPhoto);
			assertColor(Color.BLUE, otherPhoto.getPixel(160, 160));
		} finally {
			if (otherPhoto != null) otherPhoto.recycle();
		}
		File[] remaining = new File(files, "avatars").listFiles();
		assertNotNull(remaining);
		assertEquals(1, remaining.length);
		assertTrue(remaining[0].getName().endsWith(".jpg"));
	}

	private File writeImage(Bitmap bitmap, String name, Bitmap.CompressFormat format) throws Exception {
		File file = new File(fixture, name);
		try (FileOutputStream out = new FileOutputStream(file)) { assertTrue(bitmap.compress(format, 100, out)); }
		return file;
	}

	private static void assertColor(int expected, int actual) {
		assertTrue("Rojo", Math.abs(Color.red(expected) - Color.red(actual)) < 12);
		assertTrue("Verde", Math.abs(Color.green(expected) - Color.green(actual)) < 12);
		assertTrue("Azul", Math.abs(Color.blue(expected) - Color.blue(actual)) < 12);
	}

	private static void deleteFixture(File file) {
		if (file == null) return;
		File[] children = file.listFiles();
		if (children != null)
			for (File child : children) deleteFixture(child);
		assertTrue("No se pudo eliminar el fixture " + file.getName(), file.delete());
	}
}
