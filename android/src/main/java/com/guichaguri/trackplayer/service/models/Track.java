package com.guichaguri.trackplayer.service.models;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.util.Log;

import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Util;
import com.guichaguri.trackplayer.service.Utils;
import com.guichaguri.trackplayer.service.player.LocalPlayback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DATE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_GENRE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_RATING;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;

/**
 * @author Guichaguri
 */
public class Track {

    public static List<Track> createTracks(Context context, List objects, int ratingType) {
        List<Track> tracks = new ArrayList<>();

        for (Object o : objects) {
            if (o instanceof Bundle) {
                tracks.add(new Track(context, (Bundle) o, ratingType));
            } else {
                return null;
            }
        }

        return tracks;
    }

    public String id;
    public Uri uri;
    public int resourceId;
    public String key;

    public TrackType type = TrackType.DEFAULT;

    public String contentType;
    public String userAgent;

    public Uri artwork;

    public String title;
    public String artist;
    public String album;
    public String date;
    public String genre;
    public long duration;
    public Bundle originalItem;

    public RatingCompat rating;

    public Map<String, String> headers;

    public final long queueId;

    public Track(Context context, Bundle bundle, int ratingType) {


        id = bundle.getString("id");

        resourceId = Utils.getRawResourceId(context, bundle, "url");

        if (resourceId == 0) {
            uri = Utils.getUri(context, bundle, "url");
        } else {
            uri = RawResourceDataSource.buildRawResourceUri(resourceId);
        }

        String trackType = bundle.getString("type", "default");

        for (TrackType t : TrackType.values()) {
            if (t.name.equalsIgnoreCase(trackType)) {
                type = t;
                break;
            }
        }

        contentType = bundle.getString("contentType");
        userAgent = bundle.getString("userAgent");
        key = bundle.getString("key");

        Bundle httpHeaders = bundle.getBundle("headers");
        if (httpHeaders != null) {
            headers = new HashMap<>();
            for (String header : httpHeaders.keySet()) {
                headers.put(header, httpHeaders.getString(header));
            }
        }

        setMetadata(context, bundle, ratingType);

        queueId = System.currentTimeMillis();
        originalItem = bundle;
    }

    public void setMetadata(Context context, Bundle bundle, int ratingType) {
        artwork = Utils.getUri(context, bundle, "artwork");
        title = bundle.getString("title");
        artist = bundle.getString("artist");
        album = bundle.getString("album");
        date = bundle.getString("date");
        genre = bundle.getString("genre");
        duration = Utils.toMillis(bundle.getDouble("duration", 0));

        rating = Utils.getRating(bundle, "rating", ratingType);

        if (originalItem != null && originalItem != bundle)
            originalItem.putAll(bundle);
    }

    public MediaMetadataCompat.Builder toMediaMetadata() {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();

        builder.putString(METADATA_KEY_TITLE, title);
        builder.putString(METADATA_KEY_ARTIST, artist);
        builder.putString(METADATA_KEY_ALBUM, album);
        builder.putString(METADATA_KEY_DATE, date);
        builder.putString(METADATA_KEY_GENRE, genre);
        builder.putString(METADATA_KEY_MEDIA_URI, uri.toString());
        builder.putString(METADATA_KEY_MEDIA_ID, id);

        if (duration > 0) {
            builder.putLong(METADATA_KEY_DURATION, duration);
        }

        if (artwork != null) {

            builder.putString(METADATA_KEY_ART_URI, artwork.toString());
        }

        if (rating != null) {
            builder.putRating(METADATA_KEY_RATING, rating);
        }

        return builder;
    }

    public QueueItem toQueueItem() {
        MediaDescriptionCompat descr = new MediaDescriptionCompat.Builder()
                .setTitle(title)
                .setSubtitle(artist)
                .setMediaId(id)
                .setMediaUri(uri)
                .setIconUri(artwork)
                .build();

        return new QueueItem(descr, queueId);
    }

    public MediaSource toMediaSource(Context ctx, LocalPlayback playback) {
        // Updates the user agent if not set

        if (userAgent == null || userAgent.isEmpty())
            userAgent = Util.getUserAgent(ctx, "react-native-track-player");

        DataSource.Factory ds;

        if (resourceId != 0) {

            try {
                RawResourceDataSource raw = new RawResourceDataSource(ctx);
                DataSpec dataSpec;

                dataSpec = new DataSpec(uri);

                raw.open(dataSpec);
                ds = () -> raw;
            } catch (IOException ex) {
                // Should never happen
                throw new RuntimeException(ex);
            }

        } else if (Utils.isLocal(uri)) {

            // Creates a local source factory
            ds = new DefaultDataSourceFactory(ctx, userAgent);

        } else {

            // Creates a default http source factory, enabling cross protocol redirects

            DefaultHttpDataSourceFactory factory = new DefaultHttpDataSourceFactory(
                    userAgent, HttpFactoryListener,
                    DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                    DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                    true
            );

            if (headers != null) {
                factory.getDefaultRequestProperties().set(headers);
            }

            ds = playback.enableCaching(factory);


        }

        switch (type) {
            case DASH:
                return new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(ds), ds)
                        .createMediaSource(uri);
            case HLS:
                return new HlsMediaSource.Factory(ds)
                        .createMediaSource(uri);
            case SMOOTH_STREAMING:
                return new SsMediaSource.Factory(new DefaultSsChunkSource.Factory(ds), ds)
                        .createMediaSource(uri);
            default:
                if (key != null) {
                    return new ProgressiveMediaSource.Factory(ds, new DefaultExtractorsFactory()
                            .setConstantBitrateSeekingEnabled(true))
                            .setCustomCacheKey(key)
                            .createMediaSource(uri);
                } else {
                    return new ProgressiveMediaSource.Factory(ds, new DefaultExtractorsFactory()
                            .setConstantBitrateSeekingEnabled(true))
                            .createMediaSource(uri);
                }
        }
    }

    private TransferListener HttpFactoryListener = new TransferListener() {
        @Override
        public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {
            Log.d(Utils.LOG, "cache onTransferInitializing : source:" + source + " source:" + dataSpec + " source" + isNetwork + "//");
        }

        @Override
        public void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {
            Log.d(Utils.LOG, "cache onTransferStart : source:" + source + " source:" + dataSpec + " source" + isNetwork + "//");
        }

        @Override
        public void onBytesTransferred(DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
            //Log.d(Utils.LOG, "cache onBytesTransferred : source:"+source+" source:"+dataSpec+" source"+isNetwork+"bytesTransferred"+bytesTransferred+"//");
        }

        @Override
        public void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
            Log.d(Utils.LOG, "cache onTransferEnd : source:" + source + " source:" + dataSpec + " source" + isNetwork + "//");
        }
    };
}
