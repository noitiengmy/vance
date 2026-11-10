package com.maico.refurbished.patcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.ByteArrayInputStream;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Converts a user-supplied PNG/JPEG to a uniform transparent adaptive-icon
 * foreground, while retaining its aspect ratio. Never stretches a rectangle
 * into a square. This class is used only by the APK patcher, not the module.
 */
public final class IconNormalizer {
    private static final int CANVAS_SIZE = 432;
    // Android adaptive icon's guaranteed safe central area is approximately
    // two thirds of the foreground. Keep the full artwork in that area.
    private static final int ARTWORK_SIZE = 288;
    private static final int MAX_DECODED_EDGE = 1024;
    private static final int MAX_INPUT_BYTES = 24 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private IconNormalizer() { }

    public static byte[] normalize(byte[] image, int iconNumber) throws IOException {
        if (image == null || image.length < 8 || image.length > MAX_INPUT_BYTES)
            throw new IOException("Image " + iconNumber + " is empty or exceeds 24 MB");
        boolean isPng = true;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (image[i] != PNG_SIGNATURE[i]) { isPng=false; break; }
        }
        boolean isJpeg=(image[0] & 0xff)==0xff &&
                (image[1] & 0xff)==0xd8 &&
                (image[2] & 0xff)==0xff;
        if (!isPng && !isJpeg)
            throw new IOException("Icon " + iconNumber + " must be PNG or JPEG");

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image, 0, image.length, bounds);
        if (bounds.outWidth < 1 || bounds.outHeight < 1)
            throw new IOException("Image " + iconNumber + " has invalid dimensions");

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inScaled = false;
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample)
                > MAX_DECODED_EDGE && sample <= (1 << 28)) {
            sample *= 2;
        }
        options.inSampleSize = sample;

        Bitmap decoded = null;
        Bitmap output = null;
        try {
            decoded = BitmapFactory.decodeByteArray(image, 0, image.length, options);
            if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0)
                throw new IOException("Image " + iconNumber + " could not be decoded");

            // BitmapFactory doesn't apply JPEG EXIF rotation. Correct it before
            // fitting into the adaptive icon safe area.
            if (isJpeg) {
                int orientation=ExifInterface.ORIENTATION_NORMAL;
                try {
                    ExifInterface exif=new ExifInterface(new ByteArrayInputStream(image));
                    orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL);
                } catch(IOException ignored) { /* JPEG may have no EXIF. */ }
                Matrix transform=new Matrix();
                switch(orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90: transform.postRotate(90); break;
                    case ExifInterface.ORIENTATION_ROTATE_180: transform.postRotate(180); break;
                    case ExifInterface.ORIENTATION_ROTATE_270: transform.postRotate(270); break;
                    case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: transform.postScale(-1,1); break;
                    case ExifInterface.ORIENTATION_FLIP_VERTICAL: transform.postScale(1,-1); break;
                    case ExifInterface.ORIENTATION_TRANSPOSE: transform.postRotate(90); transform.postScale(-1,1); break;
                    case ExifInterface.ORIENTATION_TRANSVERSE: transform.postRotate(270); transform.postScale(-1,1); break;
                    default: break;
                }
                if(!transform.isIdentity()) {
                    Bitmap adjusted=Bitmap.createBitmap(decoded,0,0,
                            decoded.getWidth(),decoded.getHeight(),transform,true);
                    if(adjusted!=decoded) {
                        decoded.recycle();
                        decoded=adjusted;
                    }
                }
            }

            output = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
            output.eraseColor(android.graphics.Color.TRANSPARENT);
            float ratio = Math.min(
                    (float) ARTWORK_SIZE / decoded.getWidth(),
                    (float) ARTWORK_SIZE / decoded.getHeight());
            float width = decoded.getWidth() * ratio;
            float height = decoded.getHeight() * ratio;
            float left = (CANVAS_SIZE - width) / 2f;
            float top = (CANVAS_SIZE - height) / 2f;
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            paint.setDither(true);
            new Canvas(output).drawBitmap(decoded,
                    new Rect(0, 0, decoded.getWidth(), decoded.getHeight()),
                    new RectF(left, top, left + width, top + height), paint);

            ByteArrayOutputStream bytes = new ByteArrayOutputStream(128 * 1024);
            if (!output.compress(Bitmap.CompressFormat.PNG, 100, bytes))
                throw new IOException("Failed to encode image " + iconNumber + " to PNG");
            return bytes.toByteArray();
        } catch (OutOfMemoryError oom) {
            throw new IOException("Image " + iconNumber + " is too large to process", oom);
        } finally {
            if (output != null) output.recycle();
            if (decoded != null) decoded.recycle();
        }
    }
}
