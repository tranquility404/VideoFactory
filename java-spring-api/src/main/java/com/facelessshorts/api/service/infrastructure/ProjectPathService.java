package com.facelessshorts.api.service.infrastructure;
import com.facelessshorts.api.service.integration.*;

import com.facelessshorts.api.error.ApiException;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProjectPathService {
    private final Path root;
    private final Path projects;

    public ProjectPathService(@Value("${repository.root}") String repositoryRoot) {
        root = Path.of(repositoryRoot).toAbsolutePath().normalize();
        projects = root.resolve("projects").normalize();
    }

    public Path root() { return root; }
    public Path project(String id) {
        if (id == null || id.isBlank() || !Path.of(id).getFileName().toString().equals(id) || id.equals(".") || id.equals(".."))
            throw new ApiException(422, "project_id must be a non-empty directory name");
        Path result = projects.resolve(id).normalize();
        if (!result.startsWith(projects)) throw new ApiException(422, "invalid project_id");
        try { Files.createDirectories(result); } catch (IOException e) { throw new ApiException(500, "Cannot create project directory", e); }
        copyPalette(result, "music"); copyPalette(result, "sfx");
        return result;
    }
    public Path file(Path project, String name) {
        if (name == null || name.isBlank()) throw new ApiException(422, "file names must be non-empty strings");
        Path candidate = Path.of(name);
        if (candidate.isAbsolute()) throw new ApiException(422, "file names must be relative to the project");
        Path result = project.resolve(candidate).normalize();
        if (!result.startsWith(project)) throw new ApiException(422, "file names must be relative to the project");
        return result;
    }
    public Path projectFile(Path project, String name) { return file(project, name); }
    private void copyPalette(Path project, String library) {
        Path target = project.resolve(library).resolve("palette.json");
        Path source = root.resolve("media/library").resolve(library).resolve("palette.json");
        try { if (!Files.exists(target) && Files.isRegularFile(source)) { Files.createDirectories(target.getParent()); Files.copy(source, target); } }
        catch (IOException e) { throw new ApiException(500, "Cannot initialize project library", e); }
    }
}