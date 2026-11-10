package com.maico.refurbished.patcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Canonical, app-independent icon for the Change Display STATIC shortcut.
 * Drawn once per patch at a fixed 192x192 bitmap resolution, not borrowed
 * from Alias0 or from any user style. No host resource dependencies.
 */
final class ShortcutIcon {
    private ShortcutIcon(){}

    static byte[] png() throws IOException {
        final int size=192;
        Bitmap bitmap=null;
        try {
            bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bitmap);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.FILL);

            // Flat monochrome rounded tile.
            paint.setColor(Color.rgb(23,23,23));
            canvas.drawRoundRect(new RectF(8,8,184,184),40,40,paint);

            // Two overlapping launcher surfaces, visually distinct from
            // any app's icon; the switch arrow ties them together.
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(new RectF(44,48,116,120),14,14,paint);
            paint.setColor(Color.rgb(23,23,23));
            canvas.drawRoundRect(new RectF(52,56,108,112),9,9,paint);
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(new RectF(80,76,152,148),14,14,paint);
            paint.setColor(Color.rgb(23,23,23));
            canvas.drawRoundRect(new RectF(88,84,144,140),9,9,paint);

            // White two-direction switch mark.
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(9);
            canvas.drawArc(new RectF(48,35,149,152),215,127,false,paint);
            canvas.drawArc(new RectF(48,35,149,152),35,127,false,paint);
            paint.setStyle(Paint.Style.FILL);
            Path arrow1=new Path();
            arrow1.moveTo(64,35);
            arrow1.lineTo(88,40);
            arrow1.lineTo(72,56);
            arrow1.close();
            canvas.drawPath(arrow1,paint);
            Path arrow2=new Path();
            arrow2.moveTo(128,157);
            arrow2.lineTo(105,152);
            arrow2.lineTo(121,136);
            arrow2.close();
            canvas.drawPath(arrow2,paint);

            ByteArrayOutputStream output=new ByteArrayOutputStream(12000);
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,output))
                throw new IOException("Cannot encode Change Display shortcut icon");
            return output.toByteArray();
        }catch(OutOfMemoryError error){
            throw new IOException("Cannot allocate Change Display shortcut icon",error);
        }finally{
            if(bitmap!=null)bitmap.recycle();
        }
    }
}
