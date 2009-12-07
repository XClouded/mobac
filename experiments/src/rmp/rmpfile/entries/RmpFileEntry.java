/* *********************************************
 * Copyright: Andreas Sander
 *
 *
 * ********************************************* */

package rmp.rmpfile.entries;

/**
 * Interface for all files that are stored in a RMP file
 * 
 */
public interface RmpFileEntry {
	/**
	 * Returns the content of the file as byte array
	 */
	public byte[] getFileContent();

	/**
	 * Returns the name of the file without extension
	 */
	public String getFileName();

	/**
	 * Returns the extension of the file
	 */
	public String getFileExtension();
}
