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

public interface RunLengthPostingsWriter
{
  /** 
   * if postings object was loaded from a serialized object then 
   * intialize i/o for writing using this method.
   */
  void initializeIO() throws FileNotFoundException, IOException;
  /**
   * write a data record into the postings.
   * @param aDataRecord data to be written into postings.
   * @return size of postings in bytes.
   */
  public int writeString(String aDataRecord) throws IOException;
  /** close resources used by writer. */
  public void close() throws IOException;

} // RunLengthPostingsWriter
