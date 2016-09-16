package irutils;

import java.io.*;

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

public class FileRunLengthPostingsWriter implements RunLengthPostingsWriter, Serializable
{
  transient DataOutputStream postingsWriter = null;
  String directoryName;
  int nextPosting = 0;

  /**
   * @param aDirectoryName directory in which postings file resides.
   */
  public FileRunLengthPostingsWriter (String aDirectoryName)
    throws FileNotFoundException
  {
    this.postingsWriter = 
      new DataOutputStream ( new BufferedOutputStream
			     (new FileOutputStream
			      (aDirectoryName + "/postings" )));
    this.directoryName = aDirectoryName;
  }

  /** 
   * if postings object was loaded from a serialized object then 
   * intialize i/o for writing using this method.
   */
  public void initializeIO()
    throws FileNotFoundException
  {
    if (this.postingsWriter == null) {
      this.postingsWriter = 
	new DataOutputStream ( new BufferedOutputStream
			       (new FileOutputStream
				(this.directoryName + "/postings" )));
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
    this.postingsWriter.writeInt(aDataRecord.length());
    this.postingsWriter.writeBytes(aDataRecord);
    this.nextPosting = this.nextPosting + aDataRecord.length() + 4;
    return currentPosting;
  }

  /** close resources used by writer. */
  public void close()
    throws IOException
  {
    if (this.postingsWriter != null) {
      this.postingsWriter.close();
    }
  }

}// RunLengthPostingsWriter
