package irutils;
import java.io.*;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

/**
 * Dictionary Binary Search Map.
 * <p>s

 * Organization of one dictionary record:
 * <pre>
 *  +------------------------+--------------------+---------------------+
 *  | term                   | number of postings | address of postings |
 *  +------------------------+--------------------+---------------------+
 *  |<---- term length ----->|<---- 4 bytes ----->|<----- 4 bytes ----->|
 *  |<--------------------------- record length ----------------------->|
 * </pre>
 * Term Length, # of postings length and address length are the same
 * for all records in a partition.
 * </p>
 * Created: Wed Jul 25 09:09:18 2001
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: DictionaryBinSearchMap.java,v 1.5 2008/04/25 15:02:56 wrogers Exp $
 */

public class NioDictionaryBinSearchMap implements DiskBasedBinSearchMap, Serializable {
 
  /** length of integer in bytes */
  public static final int DATALENGTH = 4; /* is this right? */
  /** FileChannel for writing or reading map. */
  private transient FileChannel mapFileChannel;
  /** Memory Mapped File Buffer for writing or reading map. */
  private transient MappedByteBuffer mapByteBuffer;
  
  /** number of records in this map. */
  int numberOfRecords = 0;
  /** term length of all terms in this map. */
  private int termLength = 0;
  /** filename of map */
  String filename;

  /**
   * Instantiate a new or existing binary search map with single integers for data.
   * @param mapFilename filename of map
   * @param mode        file mode to use:
   *                    utils.DictionaryBinSearchMap.WRITE to open map
   *                    for writing, and
   *                    utils.DictionaryBinSearchMap.READ to open map
   *                    for reading. 
   */
  public NioDictionaryBinSearchMap ( String mapFilename, int mode )
    throws FileNotFoundException, IOException
  {
    if ( mode == WRITE ) {
      this.mapFileChannel = 
	new RandomAccessFile( mapFilename, "rw" ).getChannel();
      this.mapByteBuffer =
	this.mapFileChannel.map(FileChannel.MapMode.READ_WRITE, 0, 0x8FFFFFF);
    } else {
      this.mapFileChannel = 
	new RandomAccessFile( mapFilename, "nw" ).getChannel();
      long sz = this.mapFileChannel.size();
      this.mapByteBuffer = 
	this.mapFileChannel.map(FileChannel.MapMode.READ_ONLY, 0, sz);
    }
    this.filename = mapFilename;
  }

  /**
   * Write an entry into map
   * @param term Term. 
   * @param numOfPostings Number of postings assoicated with term.
   * @param data data associated with term.
   */
  public void writeEntry(String term, int numOfPostings, int data)
    throws IOException
  {
    // write dictionary entry
    this.mapByteBuffer.put(term.getBytes());
    this.mapByteBuffer.putInt(numOfPostings);
    this.mapByteBuffer.putInt(data);
    this.numberOfRecords++;
    this.termLength = term.getBytes().length;
  }

  /**
   * get data entry for term 
   * @param term term 
   * @return dictionary entry associated with term
   */
  public DictionaryEntry get(String term)
    throws IOException
  {
      if (this.mapFileChannel == null) {
	this.mapFileChannel = 
	  (new FileInputStream(new File(this.filename))).getChannel();
      }
      if (this.mapByteBuffer == null) {
	int sz = (int)this.mapFileChannel.size();
	this.mapByteBuffer = 
	  this.mapFileChannel.map(FileChannel.MapMode.READ_ONLY, 0, sz);
      }
      return MappedFileBinarySearch.dictionaryBinarySearch
	(this.mapByteBuffer, 
	 term, term.length(), this.numberOfRecords);
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
    return DATALENGTH;
  }

  /** close resources used by this map. */
  public void close()
    throws IOException
  {
    if ( this.mapFileChannel != null ) {
      this.mapFileChannel.close();
    }
  }

}// DictionaryBinSearchMap
