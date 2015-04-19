//C- -------------------------------------------------------------------
//C- Java DjVu (r) (v. 0.8)
//C- Copyright (c) 2004-2005 LizardTech, Inc.  All Rights Reserved.
//C- Java DjVu is protected by U.S. Pat. No.C- 6,058,214 and patents
//C- pending.
//C-
//C- This software is subject to, and may be distributed under, the
//C- GNU General Public License, Version 2. The license should have
//C- accompanied the software or you may obtain a copy of the license
//C- from the Free Software Foundation at http://www.fsf.org .
//C-
//C- The computer code originally released by LizardTech under this
//C- license and unmodified by other parties is deemed "the LIZARDTECH
//C- ORIGINAL CODE."  Subject to any third party intellectual property
//C- claims, LizardTech grants recipient a worldwide, royalty-free,
//C- non-exclusive license to make, use, sell, or otherwise dispose of
//C- the LIZARDTECH ORIGINAL CODE or of programs derived from the
//C- LIZARDTECH ORIGINAL CODE in compliance with the terms of the GNU
//C- General Public License.   This grant only confers the right to
//C- infringe patent claims underlying the LIZARDTECH ORIGINAL CODE to
//C- the extent such infringement is reasonably necessary to enable
//C- recipient to make, have made, practice, sell, or otherwise dispose
//C- of the LIZARDTECH ORIGINAL CODE (or portions thereof) and not to
//C- any greater extent that may be necessary to utilize further
//C- modifications or combinations.
//C-
//C- The LIZARDTECH ORIGINAL CODE is provided "AS IS" WITHOUT WARRANTY
//C- OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
//C- TO ANY WARRANTY OF NON-INFRINGEMENT, OR ANY IMPLIED WARRANTY OF
//C- MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.
//C-
//C- In addition, as a special exception, LizardTech Inc. gives permission
//C- to link the code of this program with the proprietary Java
//C- implementation provided by Sun (or other vendors as well), and
//C- distribute linked combinations including the two. You must obey the
//C- GNU General Public License in all respects for all of the code used
//C- other than the proprietary Java implementation. If you modify this
//C- file, you may extend this exception to your version of the file, but
//C- you are not obligated to do so. If you do not wish to do so, delete
//C- this exception statement from your version.
//C- -------------------------------------------------------------------
//C- Developed by Bill C. Riemers, Foxtrot Technologies Inc. as work for
//C- hire under US copyright laws.
//C- -------------------------------------------------------------------
//
package com.lizardtech.djvu;

import java.io.IOException;
import java.util.*;

  /**
 * This class is an InputStream which spans some of the accessable data in a
 * DataPool object.
 * 
 * @author Bill C. Riemers
 * @version $Revision: 1.5 $
 */
public class CachedInputStream
  extends InputStream
{
    //~ Instance fields ------------------------------------------------------

    // Buffer used to access data
    private DataPool buffer=null;
    
    // Used for the mark and reset features.
    private int markOffset=0;

    // The current position of this stream in the data pool.
    private int offset=0;

    // The end position of this stream in the data pool.
    private int endOffset=0;

    // Index of the current data block
    private int blockIndex=-1;
    
    // The data block currently being read
    private byte block[]=null;
    
    // We can assign a name to this Stream
    private String name=null;

    //~ Constructors ---------------------------------------------------------

    /**
     * Creates a new CachedInputStream object.
     */
    public CachedInputStream()
    {
    }

    public CachedInputStream(CachedInputStream toCopy) {
    	this.buffer = toCopy.buffer;
    	this.markOffset = toCopy.markOffset;
    	this.offset = toCopy.offset;
    	this.endOffset = toCopy.endOffset;
    	this.blockIndex = toCopy.blockIndex;
    	this.block = toCopy.block;
    	this.name = toCopy.name;
    }

    /**
     * Create an a new CachedInputStream.
     *
     * @param size the maximum amount of data to read from this stream
     *
     * @return the newly created CachedInputStream
     */
    public CachedInputStream createCachedInputStream(int size)
    {
      final CachedInputStream retval=new CachedInputStream(this);
      retval.setSize(size);
      return retval;
    }

    /**
     * Initialize the stream with a data source, startOffset, and endOffset.
     * 
     * @param buffer DataPool containing data.
     * @param startOffset Starting position of this stream.
     * @param endOffset Ending position of this stream.
     * 
     * @return the initialized stream
     */
    private CachedInputStream init(final DataPool buffer,final int startOffset,final int endOffset)
    {
      this.buffer=buffer;
      markOffset=offset=startOffset;
      this.endOffset=endOffset;
      return this;
    }
    
    /**
     * Initialize the stream with a data source, startOffset, and endOffset.
     *
     * @param url URL to read.
     *
     * @return the initialized stream
     */
    public CachedInputStream init(final String url, InputStateListener listener)
    {
      return init(new DataPool().init(url, listener),0, Integer.MAX_VALUE);
    }
    
    /**
     * Initialize the stream by copying the supplied input.
     *
     * @param input InputStream to copy
     *
     * @return the initialized stream
     */
    public CachedInputStream init(final InputStream input) throws IOException
    {
      if(input instanceof CachedInputStream)
      {
        final CachedInputStream i=(CachedInputStream)input;
        return init(i.buffer,i.offset,i.getEndOffset());         
      }
      else
      {
        return init(new DataPool().init(input),0, Integer.MAX_VALUE);
      }
    }

    /**
     * Query the end position.  This value may only be reduced, never increased.
     *
     * @return an offset past the last byte of this stream
     */
    public int getEndOffset()
    {
      final int endOffset=buffer.getEndOffset();
      return (endOffset<this.endOffset)?endOffset:this.endOffset;
    }

    
    /**
     * Set the maximum number of bytes left in this stream.  
     * Size may only be reduced, never increased.
     *
     * @param size the new size
     */
    public synchronized void setSize(final int size)
    {
      final long endOffset=offset+size;
      if(endOffset < this.endOffset)
      {
        this.endOffset=(int)endOffset;
      }
    }
    
    /**
     * Query how much data is available.
     *
     * @return number of buffered bytes
     */
    @Override
	public int available()
    {
      final int bufferEndOffset = buffer.getEndOffset();
      final int retval=(bufferEndOffset < endOffset)?bufferEndOffset:endOffset;
      return (retval > 0)?retval:0;
    }

    /**
     * Marks the current location for a reset() later.
     *
     * @param readLimit ignored
     */
	@Override
	public void mark(int readLimit)
    {
      markOffset = offset;
    }

    /**
     * Read the next byte of data ranged 0 to 255.  Returns -1 if an EOF has been read.
     *
     * @return next byte
     */
    @Override
	public int read()
    {
      int retval=-1;
      final int index=offset/DataPool.BLOCKSIZE;
      if(index != blockIndex)
      {
        block=buffer.getBlock(index, true);
        blockIndex=index;
        if(block == null)
        {
          offset = getEndOffset();
          blockIndex = -1;
        }
      }
      if(offset < getEndOffset())
      {
        retval=0xff&block[offset++%DataPool.BLOCKSIZE];
      }
      return retval;
    }

    /**
     * Read data into an array of bytes.
     *
     * @param b byte array to copy data to
     * @param off byte array offset to start copying to
     * @param len maximum number of bytes to copy
     *
     * @return number of bytes read
     */
    @Override
	public int read(
      final byte[] b,
      final int    off,
      int          len)
    {
      int retval=-1;
      final int index=offset/DataPool.BLOCKSIZE;
      if(index != blockIndex)
      {
        block=buffer.getBlock(index, true);
        blockIndex=index;
        if(block == null)
        {
          offset = getEndOffset();
          blockIndex = -1;
        }
      }
      if(offset < getEndOffset())
      {
        final int offset=this.offset%DataPool.BLOCKSIZE;
        retval = DataPool.BLOCKSIZE-offset;
        if(retval > len)
        {
          retval = len;
        }
        System.arraycopy(block, offset,b,off,retval);
        this.offset+=retval;
      }
      return retval;        
    }

    /**
     * Read data into an array of bytes.  The number of bytes
     * read is the array length unless an EOF is read.
     *
     * @param b byte array to copy data to
     *
     * @return number of bytes read
     */
    @Override
	public int read(byte [] b)
    {
      for(int remaining=b.length;remaining > 0;)
      {
        int retval=b.length-remaining;
        final int len=read(b, retval, remaining);
        if(len < 0)
        {
          return (retval > 0)?retval:(-1);              
        }
        remaining-=len;
      }
      return b.length;
    }

    /**
     * Read the next two bytes as a posative integer.  If EOF is reached,
     * then return -1.
     *
     * @return the value read
     */
    public int read16()
    {
      final int msb = read();

      if(msb < 0)
      {
        return msb;
      }

      final int lsb = read();

      return (lsb >= 0)
      ? ((msb << 8) | lsb)
      : (-1);
    }

    /**
     * Read the next three bytes as a posative integer.  If EOF is reached,
     * then return -1.
     *
     * @return the value read
     */
    public int read24()
    {
      final int msb = read16();

      if(msb < 0)
      {
        return msb;
      }

      final int lsb = read();

      return (lsb >= 0)
      ? ((msb << 8) | lsb)
      : (-1);
    }

    /**
     * Restore the marked position
     *
     * @throws IOException if an error occurs
     */
	@Override
	public void reset()
      throws IOException
    {
      offset = markOffset;
    }

    /**
     * Skip bytes without reading.
     *
     * @param n number of bytes to skip
     *
     * @return number of bytes actually skipped
     */
	@Override
	public long skip(final long n)
    {
      final int endOffset=getEndOffset();
      int retval=(endOffset <= n+offset)?(endOffset-offset):(int)n;
      if(retval > 0)
      {
        offset+=retval;
      }
      else
      {
        retval=0;
      }
      return retval;
    }
  
  /**
   * Convert the accessable data into a string.  First a java modified UTF-8
   * translation will be tried.  If that fails, the data will be converted
   * in the current locale encoding.  Unlike ImageInputStream.readUTF
   * the first two bytes are not a length.
   *
   * @param textsize maximum amount of data to read
   *
   * @return the newly created string
   *
   * @throws IOException if an error occurs
   */
  public String readSizedUTF(int textsize)
    throws IOException
  {
    final String retval=createCachedInputStream(textsize).readFullyUTF();
    skip(textsize);
    return retval;
  }
  
  /**
   * Convert the accessable data into a string.  First a java modified UTF-8
   * translation will be tried.  If that fails, the data will be converted
   * in the current locale encoding.  Unlike an ImageInputStream.readUTY
   * the first two bytes are not a length.
   *
   * @return the newly created string
   *
   * @throws IOException if an error occurs
   */
  public String readFullyUTF()
    throws IOException
  {
    final ByteArrayOutputStream output=new ByteArrayOutputStream();
    for(int i=read();i>=0;i=read())
    {
      output.write(i);
    }
    final byte[] array = output.toByteArray();
    output.close();
    return new String(array, 0, array.length, "UTF-8");
  }
  
  /**
   * Query the name of this stream.
   *
   * @return the stream name
   */
  public String getName()
  {
    return name;
  }
  
  /**
   * Set the name of this stream.
   *
   * @param name the stream name
   */
  public void setName(final String name)
  {
    this.name=name;
  }
  
  /**
   * Test if the underlying file has a DjVu octet signature.
   *
   * @return true if this is a valid DjVu file.
   */
  public boolean isDjVuFile()
  {
    final byte [] b=buffer.getBlock(0,true);
    return (b != null)&&(b[0] == 65)&&(b[1] == 84)&&(b[2] == 38)&&(b[3] == 84);
  }

  /**
   * Query the enumeration of IFF chunks.  Returns null, if the name is 
   * four characters long.
   *
   * @return an Enumeration of CachedInputStream or null.
   */
  public Enumeration<CachedInputStream> getIFFChunks()
  {
    IFFEnumeration retval=null;
    if ((name == null)||(name.length() != 4))
    {
      retval=new IFFEnumeration().init(this);
    }
    return retval;
  }

  public boolean isReady()
  {
	  return buffer != null && buffer.isReady();
  }
}
