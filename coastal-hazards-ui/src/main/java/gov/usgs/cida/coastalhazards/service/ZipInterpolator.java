package gov.usgs.cida.coastalhazards.service;

import gov.usgs.cida.coastalhazards.uncy.UncyZEncoder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.geotools.data.shapefile.ShpFileType;
import org.geotools.data.shapefile.ShpFiles;

public class ZipInterpolator {

	
	/** Unpack a shapefile zip file and encode uncertainty values as Z values in
	 * the coordinates of the Multilines.  Return a new zipfile.
	 * @param uploadDestinationFile
	 * @return new zipfile.
	 */
	public File explode(File uploadDestinationFile) throws Exception {
		// unpack
		File tmpDir = Files.createTempDirectory("xplode").toFile();
		File shpFile = unpack(tmpDir,uploadDestinationFile);
		
		String baseFilePath = getBaseFilePath(shpFile);
		
		UncyZEncoder zEncoder = new UncyZEncoder();
		File ptFile = zEncoder.explode(baseFilePath);
		
		File newZip = repack(tmpDir,ptFile);
		return newZip;
	}

	/** Creates a zipfile with all the shape components.
	 * 
	 * @param tmpDir
	 * @param ptFile
	 * @return
	 */
	private File repack(File tmpDir, File ptFile) throws Exception {
		ShpFiles shpFF = new ShpFiles(ptFile);
		
		Set<File> toPack = new HashSet<File>();
		
		// Map entries are URLs in external form.
		Map<ShpFileType, String> ffMap = shpFF.getFileNames();
		for (String fUrl : ffMap.values()) {
			URL url = new URL(fUrl);
			String fString = url.getFile();
			File f = new File(fString);
			if (f.exists()) {
				toPack.add(f);
			}
		}
		
		File newZip = File.createTempFile("encoded", "zip", tmpDir);
		FileOutputStream fos = new FileOutputStream(newZip);
		ZipOutputStream zos = new ZipOutputStream(fos);
		for (File f : toPack) {
			ZipEntry ze = new ZipEntry(f.getName());
			zos.putNextEntry(ze);
			
			FileInputStream fis = new FileInputStream(f);
			try {
				copy(fis,zos);
			} finally {
				zos.closeEntry();
			}
		}
		zos.close();
		fos.close();
		
		return newZip;
	}

	private void copy(InputStream fis, OutputStream zos) throws IOException {
		byte[] buf = new byte[8*1024];
		int ct = 0;
		do {
			ct = fis.read(buf);
			if (ct > 0) {
				zos.write(buf, 0, ct);
			}
		} while (ct > 0);
	}

	/**
	 * 
	 * @param tmpDir
	 * @param uploadDestinationFile 
	 * @return shortest-named .shp file found
	 */
	private File unpack(File tmpDir, File zipFile) throws IOException {
		File f = null;
		
		FileInputStream fin = new FileInputStream(zipFile);
		ZipInputStream zis = new ZipInputStream(fin);
		
		while (true) {
			ZipEntry ze = zis.getNextEntry();
			if (ze == null) {
				break;
			}
			
			File zf = new File(tmpDir,ze.getName());
			if (ze.getName().endsWith(".shp")) {
				if (f == null || f.getName().length() > ze.getName().length()) {
					f = new File(tmpDir, ze.getName());
				}
			}
			
			FileOutputStream fout = new FileOutputStream(zf);
			try {
				copy(zis,fout);
			} finally {
				fout.close();
			}
			
		}
		
		zis.close();
		fin.close();
		return f;
	}
	
	/**
	 * Returns the complete absolute file path without the extension.
	 * 
	 * @param file
	 * @return 
	 */
	private String getBaseFilePath(File file) {
		String path = file.getAbsolutePath();
		int idx = path.indexOf(".");
		if (idx > 0) {
			return path.substring(0, idx);
		} else {
			return path;
		}
	}

}
