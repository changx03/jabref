package net.sf.jabref.logic.cleanup;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.sf.jabref.logic.TypedBibEntry;
import net.sf.jabref.logic.layout.LayoutFormatterPreferences;
import net.sf.jabref.logic.util.io.FileUtil;
import net.sf.jabref.model.FieldChange;
import net.sf.jabref.model.cleanup.CleanupJob;
import net.sf.jabref.model.database.BibDatabaseContext;
import net.sf.jabref.model.entry.BibEntry;
import net.sf.jabref.model.entry.ParsedFileField;
import net.sf.jabref.model.metadata.FileDirectoryPreferences;

public class MoveFilesCleanup implements CleanupJob {

    private final BibDatabaseContext databaseContext;
    private final FileDirectoryPreferences fileDirectoryPreferences;
    private final LayoutFormatterPreferences prefs;

    private final String fileDirPattern;

    public MoveFilesCleanup(BibDatabaseContext databaseContext, String fileDirPattern,
            FileDirectoryPreferences fileDirectoryPreferences, LayoutFormatterPreferences prefs) {
        this.databaseContext = Objects.requireNonNull(databaseContext);
        this.fileDirPattern = Objects.requireNonNull(fileDirPattern);
        this.fileDirectoryPreferences = Objects.requireNonNull(fileDirectoryPreferences);
        this.prefs = Objects.requireNonNull(prefs);
    }

    @Override
    public List<FieldChange> cleanup(BibEntry entry) {
        if (!databaseContext.getMetaData().getDefaultFileDirectory().isPresent()) {
            return Collections.emptyList();
        }

        List<String> paths = databaseContext.getFileDirectories(fileDirectoryPreferences);
        String defaultFileDirectory = databaseContext.getMetaData().getDefaultFileDirectory().get();
        Optional<File> targetDirectory = FileUtil.expandFilename(defaultFileDirectory, paths);

        if (!targetDirectory.isPresent()) {
            return Collections.emptyList();
        }

        TypedBibEntry typedEntry = new TypedBibEntry(entry, databaseContext);
        List<ParsedFileField> fileList = typedEntry.getFiles();
        List<ParsedFileField> newFileList = new ArrayList<>();

        boolean changed = false;
        for (ParsedFileField fileEntry : fileList) {
            String oldFileName = fileEntry.getLink();

            Optional<File> oldFile = FileUtil.expandFilename(oldFileName, paths);
            if (!oldFile.isPresent() || !oldFile.get().exists()) {
                newFileList.add(fileEntry);
                continue;
            }

            System.out.println(fileDirPattern);
            String targetDirName = "";
            if (!fileDirPattern.isEmpty()) {
                targetDirName = FileUtil.createFileNameFromPattern(databaseContext.getDatabase(), entry, fileDirPattern,
                        prefs);
            }

            Path newTargetFile = targetDirectory.get().toPath().resolve(targetDirName).resolve(oldFile.get().getName());
            System.out.println("Target Path " + newTargetFile);

            File targetFile = new File(targetDirectory.get(), oldFile.get().getName());
            if (targetFile.exists()) {
                // We do not overwrite already existing files
                newFileList.add(fileEntry);
                continue;
            }

            oldFile.get().renameTo(targetFile);
            String newFileName = targetFile.getName();

            ParsedFileField newFileEntry = fileEntry;
            if (!oldFileName.equals(newFileName)) {
                newFileEntry = new ParsedFileField(fileEntry.getDescription(), newFileName, fileEntry.getFileType());
                changed = true;
            }
            newFileList.add(newFileEntry);
        }

        if (changed) {
            Optional<FieldChange> change = entry.setFiles(newFileList);
            if (change.isPresent()) {
                return Collections.singletonList(change.get());
            } else {
                return Collections.emptyList();
            }
        }

        return Collections.emptyList();
    }

}
