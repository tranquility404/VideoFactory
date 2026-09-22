package com.facelessshorts.api.service;
import com.facelessshorts.api.service.tools.*;
import com.facelessshorts.api.service.infrastructure.*;
import com.facelessshorts.api.service.integration.*;

import static org.junit.jupiter.api.Assertions.*;

import com.facelessshorts.api.error.ApiException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CutoutServiceTest {
    @TempDir Path temp;
    private Path project;
    private CutoutService service;

    @BeforeEach
    void setUp() throws Exception {
        project = temp.resolve("projects").resolve("cutout-test");
        Files.createDirectories(project);
        service = new CutoutService(new ProjectPathService(temp.toString()), null);
    }

    @Test
    void keyMakesWhiteBackgroundTransparentAndWritesPngMetadata() throws Exception {
        writeImage("input.png", 8, 8, (x, y) -> x >= 2 && x <= 5 && y >= 2 && y <= 5 ? Color.RED : Color.WHITE);

        var response = service.run(project, List.of("input.png", "nested/output.png", "--method", "key", "--soft", "0", "--feather", "0", "--pad", "0", "--no-trim"), "make_cutout");
        BufferedImage output = ImageIO.read(project.resolve("nested/output.png").toFile());

        assertEquals("cutout", response.get("tool"));
        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) response.get("result");
        assertEquals("key", data.get("method"));
        assertEquals(0, output.getRGB(0, 0) >>> 24);
        assertEquals(255, output.getRGB(3, 3) >>> 24);
    }

    @Test
    void noTrimPreservesDimensionsAndPadExpandsTrimmedResult() throws Exception {
        writeImage("input.png", 8, 7, (x, y) -> x >= 2 && x <= 4 && y >= 2 && y <= 4 ? Color.BLUE : Color.WHITE);

        service.run(project, List.of("input.png", "trimmed.png", "--soft", "0", "--feather", "0", "--pad", "0"), "cutout");
        service.run(project, List.of("input.png", "full.png", "--soft", "0", "--feather", "0", "--pad", "2", "--no-trim"), "cutout");
        BufferedImage trimmed = ImageIO.read(project.resolve("trimmed.png").toFile());
        BufferedImage full = ImageIO.read(project.resolve("full.png").toFile());

        assertEquals(1, trimmed.getWidth());
        assertEquals(1, trimmed.getHeight());
        assertEquals(8, full.getWidth());
        assertEquals(7, full.getHeight());
    }

    @Test
    void acceptsRembgMethodAndRejectsInvalidInputs() throws Exception {
        assertEquals(422, assertThrows(ApiException.class, () -> service.run(project, List.of("a.png", "a.png"), "cutout")).status());
        Files.writeString(project.resolve("not-image.txt"), "not an image");
        assertEquals(422, assertThrows(ApiException.class, () -> service.run(project, List.of("not-image.txt", "out.png"), "cutout")).status());
        assertEquals(422, assertThrows(ApiException.class, () -> service.run(project, List.of("a.png", "out.png", "--tol", "999"), "cutout")).status());
    }

    private void writeImage(String name, int width, int height, Pixel pixel) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, pixel.color(x, y).getRGB());
        ImageIO.write(image, "PNG", project.resolve(name).toFile());
    }

    @FunctionalInterface
    private interface Pixel { Color color(int x, int y); }
}