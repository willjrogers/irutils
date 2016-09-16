package irutils;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * RunLengthPostings.java
 *
 *
 * Created: Wed Jul 25 09:13:06 2001
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: RunLengthPostingsWriter.java,v 1.2 2001/09/07 13:32:21 wrogers Exp $
 */
// organization:
//  +------------------------+-------------------+------+
//  | byte length of posting |      posting      | .... |
//  +------------------------+-------------------+------+
//  |<------ 4 bytes ------->|<-- byte length -->|

public class NioRunLengthPostingsWriter implements RunLengthPostingsWriter, Serializable
{
  transient MappedByteBuffer postingsWriter = null;
  String directoryName;
  int nextPosting = 0;

  /**
   * @param aDirectoryName directory in which postings file resides.
   */
  public NioRunLengthPostingsWriter (String aDirectoryName)
    throws FileNotFoundException, IOException
  {
    this.postingsWriter = new RandomAccessFile
      (aDirectoryName + "/postings", "rw" ).getChannel().map
      (FileChannel.MapMode.READ_WRITE, 0, 0x8FFFFFF);
    this.directoryName = aDirectoryName;
  }

  /** 
   * if postings object was loaded from a serialized object then 
   * intialize i/o for writing using this method.
   */
  public void initializeIO()
    throws FileNotFoundException, IOException
  {
    if (this.postingsWriter == null) {
      this.postingsWriter = new RandomAccessFile
	(this.directoryName + "/postings", "nw" ).getChannel().map
	(FileChannel.MapMode.READ_WRITE, 0, 0x8FFFFFF);
    }
  }

  /**
   * write a data record into the postings.
   * @param aDataRecord data to be written into postings.
   * @return size of postings in bytes.
   */
  public int writeString(String aDataRecord)
    throws IOException
  {
    int currentPosting = this.nextPosting;
    this.postingsWriter.putInt(aDataRecord.length());
    this.postingsWriter.put(aDataRecord.getBytes());
    this.nextPosting = this.nextPosting + aDataRecord.getBytes().length + 4;
    return currentPosting;
  }

  /** close resources used by writer. */
  public void close()
    throws IOException
  {
    if (this.postingsWriter != null) {
      // no unmap method in MappedByteBuffer
      // this.postingsWriter.?();
    }
  }

}// RunLengthPostingsWriter
