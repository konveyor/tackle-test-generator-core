package org.konveyor.tackle.testgen;

import java.io.File;

public class TestUtils {

	public static boolean containsFiles(File dir) {

		for (File file : dir.listFiles()) {
			if (file.isFile()) {
				return true;
			} else {
				boolean hasFiles = containsFiles(file);
				if (hasFiles) {
					return true;
				}
			}
		}

		return false;
	}
}
