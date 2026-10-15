package com.energym.app.ui;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import static org.junit.Assert.*;

/** Comprueba la imagen y la política HTTPS sin consultar perfiles ni servicios externos. */
public final class GooglePhotoLoaderTest {
	@Test public void profileUrlRequiresHttpsWithoutEmbeddedCredentialsOrUnexpectedPorts() throws Exception {
		String google = "https://lh3.googleusercontent.com/a/photo=s96-c?size=96";
		assertEquals(google, GooglePhotoLoader.secureUrl(google).toString());
		for (String invalid : new String[]{"http://example.com/a.jpg", "file:/a.jpg", "content://photos/1",
			"https://user:password@example.com/a.jpg", "https://example.com:8443/a.jpg", "https://example.com/a.jpg#fragment"}) {
			try { GooglePhotoLoader.secureUrl(invalid); fail("Debe rechazar la dirección no permitida"); }
			catch (IOException expected) { }
		}
	}

	@Test public void imageDecodesAtAvatarSizeInsteadOfItsOriginalResolution() throws Exception {
		Bitmap source = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888);
		Bitmap result = null;
		try (ByteArrayOutputStream encoded = new ByteArrayOutputStream()) {
			source.eraseColor(Color.GREEN);
			assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, encoded));
			result = GooglePhotoLoader.decode(encoded.toByteArray());
			assertNotNull(result);
			assertTrue(result.getWidth() <= 256 && result.getHeight() <= 256);
			assertEquals(Color.GREEN, result.getPixel(result.getWidth() / 2, result.getHeight() / 2));
		} finally {
			source.recycle();
			if (result != null) result.recycle();
		}
	}

	@Test public void invalidImageCanUseTheFallbackWithoutCrashingTheView() throws Exception {
		try { GooglePhotoLoader.decode(new byte[]{1, 2, 3}); fail("Debe rechazar los bytes no válidos"); }
		catch (IOException expected) { }
	}
}
