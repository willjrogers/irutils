package irutils;
import java.io.*;

/**
 * DataBinSearchMap.java
 * <p>
 * organization of one record:
 * <pre>
 *  +------------------------+-------------------+
 *  | term                   |      data         |
 *  +------------------------+-------------------+
 *  |<---- term length ----->|<-- data length -->|
 *  |<------------- record length -------------->|
 * </pre>
 *
 *  Term Length And Data Length Is The Same For All Records In Map.
 *  </p>
 *
 * Created: Wed Jul 25 09:11:23 2001
 *
 * @author <a href="mailto: "Willie Rogers</a>
 * @version $Id: DataBinSearchMap.java,v 1.4 2001/09/20 15:03:23 wrogers Exp $
 */

public class DataBinSearchMap implements BinSearchMap, Serializable 
{

  /** data output stream for writing map. */
  private transient DataOutputStream mapWriter;
  /** random access file for reading map. */
  private transient RandomAccessFile mapRAFile;
  /** number of records in this map. */
  int numberOfRecords = 0;
  /** termLength of all terms in this map. */
  private int termLength = 0;
  /** data length of data associated with terms for each record in map */
  private int dataLength = 0;
  /** filename of map */
  String filename;

  /**
   * Instantiate a new or existing binary search map with data of a fixed length
   * @param mapFilename filename of map 
   * @param mode        file mode to use: utils.BinSearchMap.WRITE to open map for writing, and
   *                     utils.BinSearchMap.READ to open map for reading.
   */
  public DataBinSearchMap (String mapFilename, int mode )
    throws FileNotFoundException
  {
    if ( mode == WRITE ) {
      this.mapWriter = 
	new DataOutputStream ( new BufferedOutputStream
			       ( new FileOutputStream ( mapFilename )));
    } else {
      this.mapRAFile = new RandomAccessFile ( mapFilename, "r");
    }
    this.filename = mapFilename;    
  }

  /**
   * Write an entry into map.
   * @param term term 
   * @param data data to be assoicated with term
   */
  public void writeEntry(String term, byte[] data)
    throws IOException
  {
    // write dictionary entry
    this.mapWriter.writeBytes(term);
    this.mapWriter.write(data, 0, data.length);
    this.numberOfRecords++;
    this.termLength = term.length();
    this.dataLength = data.length;
  }

  /**
   * get data entry for term 
   * @param term term 
   * @return byte array containing value associated with term
   */
  public byte[] get(String term)
    throws IOException
  {
    if (this.mapRAFile == null ) {
       this.mapRAFile = new RandomAccessFile ( this.filename, "r");
    }
    return DiskBinarySearch.binarySearch(this.mapRAFile, term, term.length(), this.numberOfRecords, this.dataLength);
  }

  /**
   * @return get number of records in map
   */
  public int getNumberOfRecords()
  {
    return this.numberOfRecords;
  }

  /**
   * @return get length of data in each record
   */
  public int getDataLength()
  {
    return this.dataLength;
  }

  /** close resources used by this map. */
  public void close()
    throws IOException
  {
    if (this.mapRAFile != null ) {
      this.mapRAFile.close();
    }
    if (this.mapWriter != null ) {
      this.mapWriter.close();
    }
  }

}// DataBinSearchMap
