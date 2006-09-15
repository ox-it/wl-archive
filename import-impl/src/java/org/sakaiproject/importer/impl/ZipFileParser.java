package org.sakaiproject.importer.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.activation.MimetypesFileTypeMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.sakaiproject.importer.api.ImportDataSource;
import org.sakaiproject.importer.api.ImportFileParser;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public abstract class ZipFileParser implements ImportFileParser {
	protected MimetypesFileTypeMap mimeTypes = new MimetypesFileTypeMap();
	protected String pathToData;
	protected String localArchiveLocation;

	public boolean isValidArchive(byte[] fileData) {
		ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(fileData));
		ZipEntry entry;
		try {
			entry = (ZipEntry) zipStream.getNextEntry();
		} catch (IOException e) {
			// IOException definitely indicates not a valid archive
			return false;
		}
	    return (entry != null);
	}

	public ImportDataSource parse(byte[] fileData, String unArchiveLocation) {
		this.localArchiveLocation = unzipArchive(fileData, unArchiveLocation);
		this.pathToData = unArchiveLocation + "/" + localArchiveLocation;
		awakeFromUnzip(pathToData);
		List categories = new ArrayList();
		Collection items = new ArrayList();
		categories.addAll(getCategoriesFromArchive(pathToData));
		items.addAll(getImportableItemsFromArchive(pathToData));
		
		ImportDataSource dataSource = new BasicImportDataSource();
	    dataSource.setItemCategories(categories);
	    dataSource.setItems(items);
		return dataSource;
	}
	
	protected abstract void awakeFromUnzip(String unArchiveLocation);

	protected abstract Collection getImportableItemsFromArchive(String pathToData);

	protected abstract Collection getCategoriesFromArchive(String pathToData);

	protected String unzipArchive(byte[] fileData, String unArchiveLocation) {
		String localArchiveLocation = Long.toString(new java.util.Date().getTime());
		String pathToData = unArchiveLocation + "/" + localArchiveLocation;
		File dir = new File(pathToData); //directory where file would be saved
	    if (!dir.exists())
	    {
	      dir.mkdirs();
	    }
	    
		try
	    {
		  Set dirsMade = new TreeSet();
		  ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(fileData));
		  ZipEntry entry = (ZipEntry) zipStream.getNextEntry();
	      while (entry != null)
	      {
	        String zipName = entry.getName();
	        
	        //for attachment type files
	            // Get the directory part.
	            int ix = zipName.lastIndexOf('/');
	            if (ix > 0) {
	              String dirName = zipName.substring(0, ix);
	              if (!dirsMade.contains(dirName)) {
	                File d = new File(dir.getPath() + "/" + dirName);
	                // If it already exists as a dir, don't do anything
	                if (!(d.exists() && d.isDirectory())) {
	                  // Try to create the directory, warn if it fails
	                	if (!d.mkdirs()) {
	                        //Log.warn("Warning: unable to mkdir " + dir.getPath() + "/" + dirName);
	                      }  
	                  dirsMade.add(dirName);
	                }
	              }
	            }
	            File zipEntryFile = new File(dir.getPath() + "/" + entry.getName());
	            if (!zipEntryFile.isDirectory()) {
		            FileOutputStream ofile = new FileOutputStream(zipEntryFile);
		            byte[] buffer = new byte[1024 * 10];
		            int bytesRead;
		            while ((bytesRead = zipStream.read(buffer)) != -1)
		            {
		              ofile.write(buffer, 0, bytesRead);
		            }
	
		            ofile.close();
	            }
	            zipStream.closeEntry();
	            entry = zipStream.getNextEntry();
	      }
	    }
	    catch (Exception e5)
	    {
	      // todo handle errors
	    	e5.printStackTrace();
	    }
		return localArchiveLocation;
	}
	
	protected boolean fileExistsInArchive(String pathAndFilename, byte[] archive) {
		ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(archive));
		ZipEntry entry;
		String entryName;
		if (pathAndFilename.charAt(0) == '/') {
			pathAndFilename = pathAndFilename.substring(1);
		}
		try {
			entry = (ZipEntry) zipStream.getNextEntry();
		    while (entry != null) {
		    	entryName = entry.getName();
		    	if (entryName.equals(pathAndFilename)) return true;
		    	entry = (ZipEntry) zipStream.getNextEntry();
		    }
		    return false;
		} catch (IOException e) {
			return false;
		}
	}
	
	protected Document extractFileAsDOM(String pathAndFilename, byte[] archive) {
		ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(archive));
		ZipEntry entry;
		String entryName;
		if (pathAndFilename.charAt(0) == '/') {
			pathAndFilename = pathAndFilename.substring(1);
		}
		DocumentBuilder docBuilder;
		try {
			docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			entry = (ZipEntry) zipStream.getNextEntry();
		    while (entry != null) {
		    	entryName = entry.getName();
		    	if (entryName.equals(pathAndFilename)) {
		            return (Document) docBuilder.parse(zipStream);
		    	}
		    	entry = (ZipEntry) zipStream.getNextEntry();
		    }
		    return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			return null;
		} catch (FactoryConfigurationError e) {
			e.printStackTrace();
			return null;
		} catch (SAXException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	protected byte[] getBytesFromFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);
    
        // Get the size of the file
        long length = file.length();
    
        // You cannot create an array using a long type.
        // It needs to be an int type.
        // Before converting to an int type, check
        // to ensure that file is not larger than Integer.MAX_VALUE.
        if (length > Integer.MAX_VALUE) {
            // File is too large
        }
    
        // Create the byte array to hold the data
        byte[] bytes = new byte[(int)length];
    
        // Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
               && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
            offset += numRead;
        }
    
        // Ensure all the bytes have been read in
        if (offset < bytes.length) {
            throw new IOException("Could not completely read file "+file.getName());
        }
    
        // Close the input stream and return bytes
        is.close();
        return bytes;
    }

}