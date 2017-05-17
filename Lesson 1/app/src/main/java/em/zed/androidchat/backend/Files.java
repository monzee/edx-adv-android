package em.zed.androidchat.backend;
/*
 * This file is a part of the Lesson 1 project.
 */

import android.annotation.SuppressLint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public interface Files {

    boolean OVERWRITE = true;
    boolean APPEND = false;

    interface Read {
        void accept(FileInputStream fileInStream, boolean isNew) throws IOException;
    }

    interface Write {
        void accept(FileOutputStream fileOutStream) throws IOException;
    }

    @SuppressLint("NewApi")  // retrolambda will transform the try-let
    class Service {
        private final File rootDir;

        public Service(File rootDir) {
            if (!rootDir.isDirectory()) {
                throw new IllegalArgumentException("Not a directory.");
            }
            this.rootDir = rootDir;
        }

        public File get(String filename) {
            return new File(rootDir, filename);
        }

        public void read(String filename, Read block) throws IOException {
            File f = get(filename);
            boolean fresh = f.createNewFile();
            try (FileInputStream fileInStream = new FileInputStream(f)) {
                block.accept(fileInStream, fresh);
            }
        }

        public void write(String filename, Write block) throws IOException {
            write(filename, APPEND, block);
        }

        public void write(String filename, boolean overwrite, Write block) throws IOException {
            File f = get(filename);
            try (FileOutputStream fileOutStream = new FileOutputStream(f, !overwrite)) {
                block.accept(fileOutStream);
            }
        }

        public boolean delete(String filename) throws IOException {
            return get(filename).delete();
        }
    }

}
