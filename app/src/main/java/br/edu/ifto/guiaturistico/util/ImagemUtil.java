package br.edu.ifto.guiaturistico.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;


 // Carrega fotos do disco de forma leve:
 // - reduz o tamanho (uma foto da câmera tem vários megapixels, a tela não precisa disso);
 // - corrige a rotação, porque a câmera grava a orientação no EXIF em vez de girar os pixels.
 // Deve ser chamado FORA da thread principal.

public class ImagemUtil {

    private ImagemUtil() {}

    public static Bitmap carregar(String caminho, int tamanhoMaximo) {
        //lê só as dimensões da imagem, sem carregar os pixels
        BitmapFactory.Options opcoes = new BitmapFactory.Options();
        opcoes.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(caminho, opcoes);
        if (opcoes.outWidth <= 0 || opcoes.outHeight <= 0) return null;

        // calcula quanto reduzir (potência de 2) para ficar perto do tamanho desejado
        int reducao = 1;
        while (Math.max(opcoes.outWidth, opcoes.outHeight) / (reducao * 2) >= tamanhoMaximo)
            reducao *= 2;

        opcoes.inJustDecodeBounds = false;
        opcoes.inSampleSize = reducao;
        Bitmap imagem = BitmapFactory.decodeFile(caminho, opcoes);
        if (imagem == null) return null;

        // gira a imagem conforme a orientação gravada pela câmera
        int graus = lerRotacao(caminho);
        if (graus == 0) return imagem;
        Matrix matriz = new Matrix();
        matriz.postRotate(graus);
        return Bitmap.createBitmap(imagem, 0, 0, imagem.getWidth(), imagem.getHeight(), matriz, true);
    }

    private static int lerRotacao(String caminho) {
        try {
            ExifInterface exif = new ExifInterface(caminho);
            int orientacao = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientacao) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }
}
