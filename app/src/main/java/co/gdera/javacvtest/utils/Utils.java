package co.gdera.javacvtest.utils;

/**
 * Created by amlan on 10/6/16.
 */
public class Utils {
    public static void YUV_NV21_TO_BGR(int[] bgr, byte[] yuv, int width,int height) {
        final int frameSize = width * height;

        final int ii = 0;
        final int ij = 0;
        final int di = +1;
        final int dj = +1;

        int a = 0;
        for (int i = 0, ci = ii; i < height; ++i, ci += di) {
            for (int j = 0, cj = ij; j < width; ++j, cj += dj) {
                int y = (0xff & ((int) yuv[ci * width + cj]));
                int v = (0xff & ((int) yuv[frameSize + (ci >> 1) * width
                        + (cj & ~1) + 0]));
                int u = (0xff & ((int) yuv[frameSize + (ci >> 1) * width
                        + (cj & ~1) + 1]));
                y = y < 16 ? 16 : y;

                int a0 = 1192 * (y - 16);
                int a1 = 1634 * (v - 128);
                int a2 = 832 * (v - 128);
                int a3 = 400 * (u - 128);
                int a4 = 2066 * (u - 128);

                int r = (a0 + a1) >> 10;
                int g = (a0 - a2 - a3) >> 10;
                int b = (a0 + a4) >> 10;

                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);

                bgr[a++] = 0xff000000 | (b << 16) | (g << 8) | r;
            }
        }
    }
}
