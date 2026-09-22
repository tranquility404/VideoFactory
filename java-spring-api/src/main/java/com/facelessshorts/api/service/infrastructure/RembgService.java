package com.facelessshorts.api.service.infrastructure;
import com.facelessshorts.api.service.integration.*;

import com.facelessshorts.api.error.ApiException;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** U2-Net inference compatible with rembg's default u2net session. */
public final class RembgService {
    private static final int MODEL_SIZE = 320;
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};
    private final Path model;

    public RembgService(Path model) { this.model = model; }

    public int[][] matte(BufferedImage source) {
        if (!Files.isRegularFile(model)) {
            throw new ApiException(503, "rembg model is not available: " + model
                    + "; set REMBG_MODEL_PATH to a U2-Net ONNX model");
        }
        BufferedImage resized = resize(source, MODEL_SIZE, MODEL_SIZE);
        float[] input = new float[3 * MODEL_SIZE * MODEL_SIZE];
        for (int y = 0; y < MODEL_SIZE; y++) {
            for (int x = 0; x < MODEL_SIZE; x++) {
                int rgb = resized.getRGB(x, y);
                float[] channel = {(rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f};
                for (int c = 0; c < 3; c++) input[c * MODEL_SIZE * MODEL_SIZE + y * MODEL_SIZE + x]
                        = (channel[c] - MEAN[c]) / STD[c];
            }
        }
        try (OrtEnvironment env = OrtEnvironment.getEnvironment();
             OrtSession session = env.createSession(model.toString(), new OrtSession.SessionOptions());
             OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), new long[]{1, 3, MODEL_SIZE, MODEL_SIZE});
             OrtSession.Result result = session.run(Map.of(session.getInputNames().iterator().next(), tensor))) {
            Object value = result.get(0).getValue();
            float[][][][] output = (float[][][][]) value;
            float[][] mask = output[0][0];
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            for (float[] row : mask) for (float v : row) { min = Math.min(min, v); max = Math.max(max, v); }
            float range = max - min;
            int[][] matte = new int[source.getHeight()][source.getWidth()];
            for (int y = 0; y < matte.length; y++) for (int x = 0; x < matte[0].length; x++) {
                float sx = x * (MODEL_SIZE - 1f) / Math.max(1, source.getWidth() - 1);
                float sy = y * (MODEL_SIZE - 1f) / Math.max(1, source.getHeight() - 1);
                matte[y][x] = Math.round(255 * sampleBilinear(mask, sx, sy, min, range));
            }
            return matte;
        } catch (OrtException | ClassCastException e) {
            throw new ApiException(503, "rembg ONNX inference failed: " + e.getMessage());
        }
    }

    private static float sampleBilinear(float[][] mask, float x, float y, float min, float range) {
        if (range == 0) return 0;
        int x0 = Math.max(0, Math.min(mask[0].length - 1, (int) x));
        int y0 = Math.max(0, Math.min(mask.length - 1, (int) y));
        int x1 = Math.min(mask[0].length - 1, x0 + 1), y1 = Math.min(mask.length - 1, y0 + 1);
        float fx = x - x0, fy = y - y0;
        float top = mask[y0][x0] * (1 - fx) + mask[y0][x1] * fx;
        float bottom = mask[y1][x0] * (1 - fx) + mask[y1][x1] * fx;
        return Math.max(0, Math.min(1, ((top * (1 - fy) + bottom * fy) - min) / range));
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return result;
    }
}