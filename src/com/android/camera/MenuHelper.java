/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import com.android.camera.gallery.IImage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.Closeable;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * A utility class to handle various kinds of menu operations.
 */
public class MenuHelper {
    private static final String TAG = "MenuHelper";

    public static final int GENERIC_ITEM      = 1;
    public static final int IMAGE_SAVING_ITEM = 2;
    public static final int VIDEO_SAVING_ITEM = 3;
    public static final int IMAGE_MODE_ITEM   = 4;
    public static final int VIDEO_MODE_ITEM   = 5;
    public static final int MENU_ITEM_MAX     = 5;

    public static final int INCLUDE_ALL           = 0xFFFFFFFF;
    public static final int INCLUDE_VIEWPLAY_MENU = (1 << 0);
    public static final int INCLUDE_SHARE_MENU    = (1 << 1);
    public static final int INCLUDE_SET_MENU      = (1 << 2);
    public static final int INCLUDE_CROP_MENU     = (1 << 3);
    public static final int INCLUDE_DELETE_MENU   = (1 << 4);
    public static final int INCLUDE_ROTATE_MENU   = (1 << 5);
    public static final int INCLUDE_DETAILS_MENU  = (1 << 6);
    public static final int INCLUDE_SHOWMAP_MENU  = (1 << 7);

    public static final int MENU_SWITCH_CAMERA_MODE = 0;
    public static final int MENU_CAPTURE_PICTURE = 1;
    public static final int MENU_CAPTURE_VIDEO = 2;
    public static final int MENU_IMAGE_SHARE = 10;
    public static final int MENU_IMAGE_SET = 14;
    public static final int MENU_IMAGE_SET_WALLPAPER = 15;
    public static final int MENU_IMAGE_CROP = 18;
    public static final int MENU_IMAGE_ROTATE = 19;
    public static final int MENU_IMAGE_ROTATE_LEFT = 20;
    public static final int MENU_IMAGE_ROTATE_RIGHT = 21;
    public static final int MENU_IMAGE_TOSS = 22;
    public static final int MENU_VIDEO_PLAY = 23;
    public static final int MENU_VIDEO_SHARE = 24;

    private static final long SHARE_FILE_LENGTH_LIMIT = 3L * 1024L * 1024L;

    public static final int NO_STORAGE_ERROR = -1;
    public static final int CANNOT_STAT_ERROR = -2;
    public static final String EMPTY_STRING = "";
    public static final String JPEG_MIME_TYPE = "image/jpeg";
    // valid range is -180f to +180f
    public static final float INVALID_LATLNG = 255f;

    /** Activity result code used to report crop results.
     */
    public static final int RESULT_COMMON_MENU_CROP = 490;

    public interface MenuItemsResult {
        public void gettingReadyToOpen(Menu menu, IImage image);
        public void aboutToCall(MenuItem item, IImage image);
    }

    public interface MenuInvoker {
        public void run(MenuCallback r);
    }

    public interface MenuCallback {
        public void run(Uri uri, IImage image);
    }

    public static void closeSilently(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Throwable e) {
                // ignore
            }
        }
    }

    public static long getImageFileSize(IImage image) {
        java.io.InputStream data = image.fullSizeImageData();
        if (data == null) return -1;
        try {
            return data.available();
        } catch (java.io.IOException ex) {
            return -1;
        } finally {
            closeSilently(data);
        }
    }

    // This is a hack before we find a solution to pass a permission to other
    // applications. See bug #1735149.
    // Checks if the URI starts with "content://mms".
    public static boolean isMMSUri(Uri uri) {
        return (uri != null) &&
               uri.getScheme().equals("content") &&
               uri.getAuthority().equals("mms");
    }

    public static void enableShareMenuItem(Menu menu, boolean enabled) {
        MenuItem item = menu.findItem(MENU_IMAGE_SHARE);
        if (item != null) {
            item.setVisible(enabled);
            item.setEnabled(enabled);
        }
    }

    private static void setDetailsValue(View d, String text, int valueId) {
        ((TextView) d.findViewById(valueId)).setText(text);
    }

    private static void hideDetailsRow(View d, int rowId) {
        d.findViewById(rowId).setVisibility(View.GONE);
    }

    private static float[] getLatLng(HashMap<String, String> exifData) {
        if (exifData == null) {
            return null;
        }

        String latValue = exifData.get(ExifInterface.TAG_GPS_LATITUDE);
        String latRef = exifData.get(ExifInterface.TAG_GPS_LATITUDE_REF);
        String lngValue = exifData.get(ExifInterface.TAG_GPS_LONGITUDE);
        String lngRef = exifData.get(ExifInterface.TAG_GPS_LONGITUDE_REF);
        float[] latlng = null;

        if (latValue != null && latRef != null
                && lngValue != null && lngRef != null) {
            latlng = new float[2];
            latlng[0] = ExifInterface.convertRationalLatLonToFloat(
                    latValue, latRef);
            latlng[1] = ExifInterface.convertRationalLatLonToFloat(
                    lngValue, lngRef);
        }

        return latlng;
    }

    private static void setLatLngDetails(View d, Activity context,
            HashMap<String, String> exifData) {
        float[] latlng = getLatLng(exifData);
        if (latlng != null) {
            setDetailsValue(d, String.valueOf(latlng[0]),
                    R.id.details_latitude_value);
            setDetailsValue(d, String.valueOf(latlng[1]),
                     R.id.details_longitude_value);
            setReverseGeocodingDetails(d, context, latlng[0], latlng[1]);
        } else {
            hideDetailsRow(d, R.id.details_latitude_row);
            hideDetailsRow(d, R.id.details_longitude_row);
            hideDetailsRow(d, R.id.details_location_row);
        }
    }

    private static void setReverseGeocodingDetails(View d, Activity context,
                                                   float lat, float lng) {
        // Fill in reverse-geocoded address
        String value = EMPTY_STRING;
        if (lat == INVALID_LATLNG || lng == INVALID_LATLNG) {
            hideDetailsRow(d, R.id.details_location_row);
            return;
        }

        try {
            Geocoder geocoder = new Geocoder(context);
            List<Address> address = geocoder.getFromLocation(lat, lng, 1);
            StringBuilder sb = new StringBuilder();
            for (Address addr : address) {
                int index = addr.getMaxAddressLineIndex();
                sb.append(addr.getAddressLine(index));
            }
            value = sb.toString();
        } catch (IOException ex) {
            // Ignore this exception.
            value = EMPTY_STRING;
            Log.e(TAG, "Geocoder exception: ", ex);
        } catch (RuntimeException ex) {
            // Ignore this exception.
            value = EMPTY_STRING;
            Log.e(TAG, "Geocoder exception: ", ex);
        }
        if (value != EMPTY_STRING) {
            setDetailsValue(d, value, R.id.details_location_value);
        } else {
            hideDetailsRow(d, R.id.details_location_row);
        }
    }

    private static HashMap<String, String> getExifData(IImage image) {
        if (!JPEG_MIME_TYPE.equals(image.getMimeType())) {
            return null;
        }

        ExifInterface exif = new ExifInterface(image.getDataPath());
        HashMap<String, String> exifData = null;
        if (exif != null) {
            exifData = exif.getAttributes();
        }
        return exifData;
    }
    // Called when "Show on Maps" is clicked.
    // Displays image location on Google Maps for further operations.
    private static boolean onShowMapClicked(MenuInvoker onInvoke,
                                            final Handler handler,
                                            final Activity activity) {
        onInvoke.run(new MenuCallback() {
            public void run(Uri u, IImage image) {
                if (image == null) {
                    return;
                }
                float[] latlng = getLatLng(getExifData(image));
                if (latlng == null) {
                    handler.post(new Runnable() {
                        public void run() {
                            Toast.makeText(activity,
                                    R.string.no_location_image,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                // Can't use geo:latitude,longitude because it only centers
                // the MapView to specified location, but we need a bubble
                // for further operations (routing to/from).
                // The q=(lat, lng) syntax is suggested by geo-team.
                String uri = "http://maps.google.com/maps?f=q&" +
                        "q=(" + latlng[0] + "," + latlng[1] + ")";
                activity.startActivity(new Intent(
                        android.content.Intent.ACTION_VIEW,
                        Uri.parse(uri)));
            }
        });
        return true;
    }

    private static void hideExifInformation(View d) {
        hideDetailsRow(d, R.id.details_resolution_row);
        hideDetailsRow(d, R.id.details_make_row);
        hideDetailsRow(d, R.id.details_model_row);
        hideDetailsRow(d, R.id.details_whitebalance_row);
        hideDetailsRow(d, R.id.details_latitude_row);
        hideDetailsRow(d, R.id.details_longitude_row);
        hideDetailsRow(d, R.id.details_location_row);
    }

    private static void showExifInformation(IImage image, View d, Activity activity) {
        HashMap<String, String> exifData = getExifData(image);
        if (exifData == null) {
            hideExifInformation(d);
            return;
        }

        String value = exifData.get(ExifInterface.TAG_MAKE);
        if (value != null) {
            setDetailsValue(d, value, R.id.details_make_value);
        } else {
            hideDetailsRow(d, R.id.details_make_row);
        }

        value = exifData.get(ExifInterface.TAG_MODEL);
        if (value != null) {
            setDetailsValue(d, value, R.id.details_model_value);
        } else {
            hideDetailsRow(d, R.id.details_model_row);
        }

        value = exifData.get(ExifInterface.TAG_WHITE_BALANCE);
        if (value != null) {
            value = ExifInterface.whiteBalanceToString(
                    Integer.parseInt(value));
        }
        if (value != null && value != EMPTY_STRING) {
            setDetailsValue(d, value, R.id.details_whitebalance_value);
        } else {
            hideDetailsRow(d, R.id.details_whitebalance_row);
        }

        setLatLngDetails(d, activity, exifData);
    }

    // Called when "Details" is clicked.
    // Displays detailed information about the image/video.
    private static boolean onDetailsClicked(MenuInvoker onInvoke,
                                            final Handler handler,
                                            final Activity activity,
                                            final boolean isImage) {
        onInvoke.run(new MenuCallback() {
            public void run(Uri u, IImage image) {
                if (image == null) {
                    return;
                }

                final AlertDialog.Builder builder =
                        new AlertDialog.Builder(activity);

                final View d = View.inflate(activity, R.layout.detailsview,
                        null);

                ImageView imageView = (ImageView) d.findViewById(
                        R.id.details_thumbnail_image);
                imageView.setImageBitmap(image.miniThumbBitmap());

                TextView textView = (TextView) d.findViewById(
                        R.id.details_image_title);
                textView.setText(image.getDisplayName());

                long length = getImageFileSize(image);
                String lengthString = length < 0
                        ? EMPTY_STRING
                        : Formatter.formatFileSize(activity, length);
                ((TextView) d
                    .findViewById(R.id.details_file_size_value))
                    .setText(lengthString);

                int dimensionWidth = 0;
                int dimensionHeight = 0;
                if (isImage) {
                    // getWidth is much slower than reading from EXIF
                    dimensionWidth = image.getWidth();
                    dimensionHeight = image.getHeight();
                    d.findViewById(R.id.details_duration_row)
                            .setVisibility(View.GONE);
                    d.findViewById(R.id.details_frame_rate_row)
                            .setVisibility(View.GONE);
                    d.findViewById(R.id.details_bit_rate_row)
                            .setVisibility(View.GONE);
                    d.findViewById(R.id.details_format_row)
                            .setVisibility(View.GONE);
                    d.findViewById(R.id.details_codec_row)
                            .setVisibility(View.GONE);
                } else {
                    MediaMetadataRetriever retriever
                            = new MediaMetadataRetriever();
                    try {
                        retriever.setMode(MediaMetadataRetriever
                                .MODE_GET_METADATA_ONLY);
                        retriever.setDataSource(image.getDataPath());
                        try {
                            dimensionWidth = Integer.parseInt(
                                    retriever.extractMetadata(
                                    MediaMetadataRetriever
                                    .METADATA_KEY_VIDEO_WIDTH));
                            dimensionHeight = Integer.parseInt(
                                    retriever.extractMetadata(
                                    MediaMetadataRetriever
                                    .METADATA_KEY_VIDEO_HEIGHT));
                        } catch (NumberFormatException e) {
                            dimensionWidth = 0;
                            dimensionHeight = 0;
                        }

                        try {
                            int durationMs = Integer.parseInt(
                                    retriever.extractMetadata(
                                    MediaMetadataRetriever
                                    .METADATA_KEY_DURATION));
                            String durationValue = formatDuration(
                                    activity, durationMs);
                            ((TextView) d.findViewById(
                                R.id.details_duration_value))
                                .setText(durationValue);
                        } catch (NumberFormatException e) {
                            d.findViewById(
                                    R.id.details_frame_rate_row)
                                    .setVisibility(View.GONE);
                        }

                        try {
                            String frameRate = String.format(
                                    activity.getString(R.string.details_fps),
                                    Integer.parseInt(
                                            retriever.extractMetadata(
                                            MediaMetadataRetriever
                                            .METADATA_KEY_FRAME_RATE)));
                            ((TextView) d.findViewById(
                                R.id.details_frame_rate_value))
                                .setText(frameRate);
                        } catch (NumberFormatException e) {
                            d.findViewById(
                                    R.id.details_frame_rate_row)
                                    .setVisibility(View.GONE);
                        }

                        try {
                            long bitRate = Long.parseLong(
                                    retriever.extractMetadata(
                                    MediaMetadataRetriever
                                    .METADATA_KEY_BIT_RATE));
                            String bps;
                            if (bitRate < 1000000) {
                                bps = String.format(
                                        activity.getString(
                                        R.string.details_kbps),
                                        bitRate / 1000);
                            } else {
                                bps = String.format(
                                        activity.getString(
                                        R.string.details_mbps),
                                        (bitRate) / 1000000.0);
                            }
                            ((TextView) d.findViewById(
                                    R.id.details_bit_rate_value))
                                    .setText(bps);
                        } catch (NumberFormatException e) {
                            d.findViewById(R.id.details_bit_rate_row)
                                    .setVisibility(View.GONE);
                        }

                        String format = retriever.extractMetadata(
                                MediaMetadataRetriever
                                .METADATA_KEY_VIDEO_FORMAT);
                        ((TextView) d.findViewById(
                                R.id.details_format_value))
                                .setText(format);

                        String codec = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_CODEC);
                        if (codec != null) {
                            setDetailsValue(d, codec, R.id.details_codec_value);
                        } else {
                            hideDetailsRow(d, R.id.details_codec_row);
                        }

                    } catch (RuntimeException ex) {
                        // Assume this is a corrupt video file.
                    } finally {
                        try {
                            retriever.release();
                        } catch (RuntimeException ex) {
                            // Ignore failures while cleaning up.
                        }
                    }
                }

                String value = null;
                if (dimensionWidth > 0 && dimensionHeight > 0) {
                    value = String.format(
                            activity.getString(R.string.details_dimension_x),
                            dimensionWidth, dimensionHeight);
                }

                if (value != null) {
                    setDetailsValue(d, value, R.id.details_resolution_value);
                } else {
                    hideDetailsRow(d, R.id.details_resolution_row);
                }

                value = EMPTY_STRING;
                long dateTaken = image.getDateTaken();
                if (dateTaken != 0) {
                    Date date = new Date(image.getDateTaken());
                    SimpleDateFormat dateFormat = new SimpleDateFormat();
                    value = dateFormat.format(date);
                }
                if (value != EMPTY_STRING) {
                    setDetailsValue(d, value, R.id.details_date_taken_value);
                } else {
                    hideDetailsRow(d, R.id.details_date_taken_row);
                }

                // Show more EXIF header details for JPEG images.
                if (JPEG_MIME_TYPE.equals(image.getMimeType())) {
                    showExifInformation(image, d, activity);
                } else {
                    hideExifInformation(d);
                }

                builder.setNeutralButton(R.string.details_ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();
                            }
                        });

                handler.post(
                        new Runnable() {
                            public void run() {
                                builder.setIcon(
                                        android.R.drawable.ic_dialog_info)
                                        .setTitle(R.string.details_panel_title)
                                        .setView(d)
                                        .show();
                            }
                        });
            }
        });
        return true;
    }

    // Called when "Rotate left" or "Rotate right" is clicked.
    private static boolean onRotateClicked(MenuInvoker onInvoke,
            final int degree) {
        onInvoke.run(new MenuCallback() {
            public void run(Uri u, IImage image) {
                if (image == null || image.isReadonly()) {
                    return;
                }
                image.rotateImageBy(degree);
            }
        });
        return true;
    }

    // Called when "Crop" is clicked.
    private static boolean onCropClicked(MenuInvoker onInvoke,
                                         final Activity activity) {
        onInvoke.run(new MenuCallback() {
            public void run(Uri u, IImage image) {
                if (u == null) {
                    return;
                }

                Intent cropIntent = new Intent();
                cropIntent.setClass(activity, CropImage.class);
                cropIntent.setData(u);
                activity.startActivityForResult(cropIntent,
                        RESULT_COMMON_MENU_CROP);
            }
        });
        return true;
    }

    // Called when "Set as" is clicked.
    private static boolean onSetAsClicked(MenuInvoker onInvoke,
                                          final Activity activity) {
        onInvoke.run(new MenuCallback() {
            public void run(Uri u, IImage image) {
                if (u == null || image == null) {
                    return;
                }

                Intent intent = new Intent(Intent.ACTION_ATTACH_DATA);
                intent.setDataAndType(u, image.getMimeType());
                intent.putExtra("mimeType", image.getMimeType());
                activity.startActivity(Intent.createChooser(intent,
                        activity.getText(R.string.setImage)));
            }
        });
        return true;
    }

    // Called when "Share" is clicked.
    private static boolean onImageShareClicked(MenuInvoker onInvoke,
            final Activity activity, final boolean isImage) {
        onInvoke.run(new MenuCallback() {
            public void run(Uri u, IImage image) {
                if (image == null) return;
                if (!isImage && getImageFileSize(image)
                        > SHARE_FILE_LENGTH_LIMIT) {
                    Toast.makeText(activity,
                            R.string.too_large_to_attach,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                String mimeType = image.getMimeType();
                intent.setType(mimeType);
                intent.putExtra(Intent.EXTRA_STREAM, u);
                boolean isImage = ImageManager.isImageMimeType(mimeType);
                try {
                    activity.startActivity(Intent.createChooser(intent,
                            activity.getText(isImage
                            ? R.string.sendImage
                            : R.string.sendVideo)));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(activity, isImage
                            ? R.string.no_way_to_share_image
                            : R.string.no_way_to_share_video,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        return true;
    }

    // Called when "Play" is clicked.
    private static boolean onViewPlayClicked(MenuInvoker onInvoke,
            final Activity activity) {
        onInvoke.run(new MenuCallback() {
            public void run(Uri uri, IImage image) {
                if (image != null) {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            image.fullSizeImageUri());
                    activity.startActivity(intent);
                }
            }});
        return true;
    }

    static MenuItemsResult addImageMenuItems(
            Menu menu,
            int inclusions,
            final boolean isImage,
            final Activity activity,
            final Handler handler,
            final Runnable onDelete,
            final MenuInvoker onInvoke) {
        final ArrayList<MenuItem> requiresWriteAccessItems =
                new ArrayList<MenuItem>();
        final ArrayList<MenuItem> requiresNoDrmAccessItems =
                new ArrayList<MenuItem>();

        if (isImage && ((inclusions & INCLUDE_ROTATE_MENU) != 0)) {
            SubMenu rotateSubmenu = menu.addSubMenu(IMAGE_SAVING_ITEM,
                    MENU_IMAGE_ROTATE, 40, R.string.rotate)
                    .setIcon(android.R.drawable.ic_menu_rotate);
            // Don't show the rotate submenu if the item at hand is read only
            // since the items within the submenu won't be shown anyway. This
            // is really a framework bug in that it shouldn't show the submenu
            // if the submenu has no visible items.
            requiresWriteAccessItems.add(rotateSubmenu.getItem());
            requiresWriteAccessItems.add(
                    rotateSubmenu.add(0, MENU_IMAGE_ROTATE_LEFT, 50,
                    R.string.rotate_left)
                    .setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            return onRotateClicked(onInvoke, -90);
                        }
                    }).setAlphabeticShortcut('l'));
            requiresWriteAccessItems.add(
                    rotateSubmenu.add(0, MENU_IMAGE_ROTATE_RIGHT, 60,
                    R.string.rotate_right)
                    .setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            return onRotateClicked(onInvoke, 90);
                        }
                    }).setAlphabeticShortcut('r'));
        }

        if (isImage && ((inclusions & INCLUDE_CROP_MENU) != 0)) {
            MenuItem autoCrop = menu.add(IMAGE_SAVING_ITEM, MENU_IMAGE_CROP, 73,
                    R.string.camera_crop);
            autoCrop.setIcon(android.R.drawable.ic_menu_crop);
            autoCrop.setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            return onCropClicked(onInvoke, activity);
                        }
                    });
            requiresWriteAccessItems.add(autoCrop);
        }

        if (isImage && ((inclusions & INCLUDE_SET_MENU) != 0)) {
            MenuItem setMenu = menu.add(IMAGE_SAVING_ITEM, MENU_IMAGE_SET, 75,
                    R.string.camera_set);
            setMenu.setIcon(android.R.drawable.ic_menu_set_as);
            setMenu.setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            return onSetAsClicked(onInvoke, activity);
                        }
                    });
        }

        if ((inclusions & INCLUDE_SHARE_MENU) != 0) {
            MenuItem item1 = menu.add(IMAGE_SAVING_ITEM, MENU_IMAGE_SHARE, 10,
                    R.string.camera_share).setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            return onImageShareClicked(onInvoke, activity,
                                    isImage);
                        }
                    });
            item1.setIcon(android.R.drawable.ic_menu_share);
            MenuItem item = item1;
            requiresNoDrmAccessItems.add(item);
        }

        if ((inclusions & INCLUDE_DELETE_MENU) != 0) {
            MenuItem deleteItem = menu.add(IMAGE_SAVING_ITEM, MENU_IMAGE_TOSS,
                    70, R.string.camera_toss);
            requiresWriteAccessItems.add(deleteItem);
            deleteItem.setOnMenuItemClickListener(
                    new MenuItem.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            deleteImpl(activity, onDelete, isImage);
                            return true;
                        }
                    })
                    .setAlphabeticShortcut('d')
                    .setIcon(android.R.drawable.ic_menu_delete);
        }

        if ((inclusions & INCLUDE_DETAILS_MENU) != 0) {
            MenuItem detailsMenu = menu.add(0, 0, 80, R.string.details)
            .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    return onDetailsClicked(onInvoke, handler, activity,
                            isImage);
                }
            });
            detailsMenu.setIcon(R.drawable.ic_menu_view_details);
        }

        if ((isImage) && ((inclusions & INCLUDE_SHOWMAP_MENU) != 0)) {
            menu.add(0, 0, 80, R.string.show_on_map)
                .setOnMenuItemClickListener(
                        new MenuItem.OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                return onShowMapClicked(onInvoke,
                                        handler, activity);
                            }
                        }).setIcon(R.drawable.ic_menu_3d_globe);
        }

        if ((!isImage) && ((inclusions & INCLUDE_VIEWPLAY_MENU) != 0)) {
            menu.add(VIDEO_SAVING_ITEM, MENU_VIDEO_PLAY, 0, R.string.video_play)
                .setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    return onViewPlayClicked(onInvoke, activity);
                }
            });
        }

        return new MenuItemsResult() {
            public void gettingReadyToOpen(Menu menu, IImage image) {
                // protect against null here.  this isn't strictly speaking
                // required but if a client app isn't handling sdcard removal
                // properly it could happen
                if (image == null) {
                    return;
                }
                boolean readOnly = image.isReadonly();
                boolean isDrm = image.isDrm();

                for (MenuItem item : requiresWriteAccessItems) {
                    item.setVisible(!readOnly);
                    item.setEnabled(!readOnly);
                }
                for (MenuItem item : requiresNoDrmAccessItems) {
                    item.setVisible(!isDrm);
                    item.setEnabled(!isDrm);
                }
            }

            // must override abstract method
            public void aboutToCall(MenuItem menu, IImage image) {
            }
        };
    }

    static void deletePhoto(Activity activity, Runnable onDelete) {
        deleteImpl(activity, onDelete, true);
    }

    static void deleteVideo(Activity activity, Runnable onDelete) {
        deleteImpl(activity, onDelete, false);
    }

    static void deleteImage(Activity activity, Runnable onDelete,
            IImage image) {
        if (image != null) {
            deleteImpl(activity, onDelete, ImageManager.isImage(image));
        }
    }

    private static void deleteImpl(Activity activity, final Runnable onDelete,
            boolean isPhoto) {
        boolean confirm = android.preference.PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getBoolean("pref_gallery_confirm_delete_key", true);
        if (!confirm) {
            if (onDelete != null) {
                onDelete.run();
            }
        } else {
            displayDeleteDialog(activity, onDelete, isPhoto);
        }
    }

    private static void displayDeleteDialog(Activity activity,
            final Runnable onDelete, boolean isPhoto) {
        android.app.AlertDialog.Builder b =
                new android.app.AlertDialog.Builder(activity);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setTitle(R.string.confirm_delete_title);
        b.setMessage(isPhoto
                ? R.string.confirm_delete_message
                : R.string.confirm_delete_video_message);
        b.setPositiveButton(android.R.string.ok,
                new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface v,
                                        int x) {
                        if (onDelete != null) {
                            onDelete.run();
                        }
                    }
                });
        b.setNegativeButton(android.R.string.cancel,
                new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface v,
                                        int x) {
                    }
                });
        b.create().show();
    }

    static void addSwitchModeMenuItem(Menu menu, final Activity activity,
            final boolean switchToVideo) {
        int group = switchToVideo
                ? MenuHelper.IMAGE_MODE_ITEM
                : MenuHelper.VIDEO_MODE_ITEM;
        int labelId = switchToVideo
                ? R.string.switch_to_video_lable
                : R.string.switch_to_camera_lable;
        int iconId = switchToVideo
                ? R.drawable.ic_menu_camera_video_view
                : android.R.drawable.ic_menu_camera;
        MenuItem item = menu.add(group, MENU_SWITCH_CAMERA_MODE, 0, labelId)
                .setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        return onSwitchModeClicked(activity, switchToVideo);
                    }
                });
        item.setIcon(iconId);
    }

    private static boolean onSwitchModeClicked(Activity activity,
                boolean switchToVideo) {
        String action = switchToVideo
                ? MediaStore.INTENT_ACTION_VIDEO_CAMERA
                : MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA;
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        activity.startActivity(intent);
        return true;
    }

    public static void gotoCameraMode(Activity activity) {
        onSwitchModeClicked(activity, false);
    }

    public static void gotoVideoMode(Activity activity) {
        onSwitchModeClicked(activity, true);
    }

    static void gotoStillImageCapture(Activity activity) {
        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Could not start still image capture activity", e);
        }
    }

    static void gotoCameraImageGallery(Activity activity) {
        gotoGallery(activity, R.string.gallery_camera_bucket_name,
                ImageManager.INCLUDE_IMAGES);
    }

    static void gotoCameraVideoGallery(Activity activity) {
        gotoGallery(activity, R.string.gallery_camera_videos_bucket_name,
                ImageManager.INCLUDE_VIDEOS);
    }

    private static void gotoGallery(Activity activity, int windowTitleId,
            int mediaTypes) {
        Uri target = Images.Media.INTERNAL_CONTENT_URI.buildUpon()
                .appendQueryParameter("bucketId",
                ImageManager.CAMERA_IMAGE_BUCKET_ID).build();
        Intent intent = new Intent(Intent.ACTION_VIEW, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("windowTitle", activity.getString(windowTitleId));
        intent.putExtra("mediaTypes", mediaTypes);

        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Could not start gallery activity", e);
        }
    }

    static void addCapturePictureMenuItems(Menu menu, final Activity activity) {
        menu.add(0, MENU_CAPTURE_PICTURE, 1, R.string.capture_picture)
                .setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        return onCapturePictureClicked(activity);
                    }
                }).setIcon(android.R.drawable.ic_menu_camera);
    }

    private static boolean onCapturePictureClicked(Activity activity) {
        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            activity.startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            // Ignore exception
        }
        return true;
    }

    static void addCaptureVideoMenuItems(Menu menu, final Activity activity) {
        menu.add(0, MENU_CAPTURE_VIDEO, 2, R.string.capture_video)
                .setOnMenuItemClickListener(
                new MenuItem.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        return onCaptureVideoClicked(activity);
                    }
                }).setIcon(R.drawable.ic_menu_camera_video_view);
    }

    private static boolean onCaptureVideoClicked(Activity activity) {
        Intent intent = new Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            activity.startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            // Ignore exception
        }
        return true;
    }

    public static void addCaptureMenuItems(Menu menu, final Activity activity) {
        addCapturePictureMenuItems(menu, activity);
        addCaptureVideoMenuItems(menu, activity);
    }

    public static String formatDuration(final Activity activity,
            int durationMs) {
        int duration = durationMs / 1000;
        int h = duration / 3600;
        int m = (duration - h * 3600) / 60;
        int s = duration - (h * 3600 + m * 60);
        String durationValue;
        if (h == 0) {
            durationValue = String.format(
                    activity.getString(R.string.details_ms), m, s);
        } else {
            durationValue = String.format(
                    activity.getString(R.string.details_hms), h, m, s);
        }
        return durationValue;
    }

    public static void showStorageToast(Activity activity) {
        showStorageToast(activity, calculatePicturesRemaining());
    }

    public static void showStorageToast(Activity activity, int remaining) {
        String noStorageText = null;

        if (remaining == MenuHelper.NO_STORAGE_ERROR) {
            String state = Environment.getExternalStorageState();
            if (state == Environment.MEDIA_CHECKING) {
                noStorageText = activity.getString(R.string.preparing_sd);
            } else {
                noStorageText = activity.getString(R.string.no_storage);
            }
        } else if (remaining < 1) {
            noStorageText = activity.getString(R.string.not_enough_space);
        }

        if (noStorageText != null) {
            Toast.makeText(activity, noStorageText, 5000).show();
        }
    }

    public static int calculatePicturesRemaining() {
        try {
            if (!ImageManager.hasStorage()) {
                return NO_STORAGE_ERROR;
            } else {
                String storageDirectory =
                        Environment.getExternalStorageDirectory().toString();
                StatFs stat = new StatFs(storageDirectory);
                float remaining = ((float) stat.getAvailableBlocks()
                        * (float) stat.getBlockSize()) / 400000F;
                return (int) remaining;
            }
        } catch (Exception ex) {
            // if we can't stat the filesystem then we don't know how many
            // pictures are remaining.  it might be zero but just leave it
            // blank since we really don't know.
            return CANNOT_STAT_ERROR;
        }
    }
}
