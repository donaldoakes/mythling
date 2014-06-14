package com.oakesville.mythling.media;

import java.io.UnsupportedEncodingException;

import com.oakesville.mythling.media.MediaSettings.MediaType;

public class Song extends Item
{
  public static final String ARTWORK_LEVEL_ALBUM = "albumArtwork";
  public static final String ARTWORK_LEVEL_SONG = "songArtwork";
  
  public Song(String id, String title)
  {
    super(id, title);
  }
  
  private int albumArtId;
  public int getAlbumArtId() { return albumArtId; }
  public void setAlbumArtId(int id) { this.albumArtId = id; }
  
  public MediaType getType()
  {
    return MediaType.music;
  }

  public String getTypeTitle()
  {
    return "Song";
  }
  
  @Override
  public ArtworkDescriptor getArtworkDescriptor(String storageGroup)
  {
    if (albumArtId == 0)
      return null;

    // actually storageGroup is artwork level (album or song)
    final boolean songLevelArt = ARTWORK_LEVEL_SONG.equals(storageGroup);

    return new ArtworkDescriptor(storageGroup) 
    {
      public String getArtworkPath()
      {
        // cache at album level 
        return getStorageGroup() + (songLevelArt ? "" : ("/" + getId()));
      }
      
      public String getArtworkContentServicePath() throws UnsupportedEncodingException
      {
        return "GetAlbumArt?Id=" + getAlbumArtId();
      }
    };
  }    
}