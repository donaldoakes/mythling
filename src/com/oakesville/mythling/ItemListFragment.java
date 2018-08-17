/**
 * Copyright 2015 Donald Oakes
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
package com.oakesville.mythling;

import com.oakesville.mythling.app.AppSettings;

import android.app.Activity;
import android.app.ListFragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.ListView;
import io.oakesville.media.Item;
import io.oakesville.media.Listable;
import io.oakesville.media.Recording;

public class ItemListFragment extends ListFragment {

    private MediaActivity mediaActivity;
    private ListableListAdapter adapter;
    private String path;
    private int preSelIdx = -1;


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        path = getArguments().getString(MediaActivity.PATH);
        preSelIdx = getArguments().getInt(MediaActivity.SEL_ITEM_INDEX);
        mediaActivity = (MediaActivity) activity;
        populate();
    }

    private void populate() {
        adapter = new ListableListAdapter(mediaActivity, mediaActivity.getListables(path).toArray(new Listable[0]), mediaActivity.isTv());
        setListAdapter(adapter);
    }

    @Override
    public void onActivityCreated(Bundle savedState) {
        super.onActivityCreated(savedState);

        // setup similar to the list view in layout/split.xml
        int padding = mediaActivity.getAppSettings().dpToPx(5);
        if (mediaActivity.getAppSettings().isFireTv()) {
            getListView().setDivider(getResources().getDrawable(R.color.divider_blue));
            getListView().setDividerHeight(1);
        }
        else {
            getListView().setDivider(getResources().getDrawable(android.R.color.transparent));
            getListView().setDividerHeight(padding);
        }

        getListView().setPadding(padding, padding, padding, padding);

        if (!mediaActivity.getAppSettings().isFireTv())
            getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        if (preSelIdx >= 0) {
            // only has effect for Fire TV, which is fine
            adapter.setSelection(preSelIdx);
            getListView().setSelection(preSelIdx);
            getListView().setItemChecked(preSelIdx, true);
            getListView().requestFocus();
        }

        registerForContextMenu(getListView());

        if (mediaActivity.getAppSettings().isTv()) {
            getListView().setOnKeyListener(new OnKeyListener() {
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                            mediaActivity.getListView().requestFocus();
                            return true;
                        }
                        else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            int pos = getListView().getSelectedItemPosition();
                            getListView().performItemClick(getListAdapter().getView(pos, null, null), pos, getListAdapter().getItemId(pos));
                            return true;
                        }
                    }
                    return false;
                }
            });

            boolean grab = getArguments() == null ? false : getArguments().getBoolean(MediaActivity.GRAB_FOCUS);
            if (grab)
                getListView().requestFocus();
        }
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);

        AppSettings settings = mediaActivity == null ? null : mediaActivity.getAppSettings();
        Object listable = getListView().getItemAtPosition(position);
        if (listable instanceof Item && settings != null && AppSettings.ARTWORK_NONE.equals(settings.getArtworkStorageGroup(((Item)listable).getType()))) {
            Item item = (Item)listable;
            mediaActivity.playItem(item);
        }
        else {
            Uri uri = new Uri.Builder().path(path).build();
            Intent intent = new Intent(Intent.ACTION_VIEW, uri, mediaActivity.getApplicationContext(), MediaListActivity.class);
            intent.putExtra(MediaActivity.CURRENT_TOP, listView.getFirstVisiblePosition());
            View topV = listView.getChildAt(0);
            intent.putExtra(MediaActivity.TOP_OFFSET, (topV == null) ? 0 : topV.getTop());
            intent.putExtra(MediaActivity.SEL_ITEM_INDEX, position);
            intent.putExtra(MediaActivity.GRAB_FOCUS, true);
            startActivity(intent);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (v == getListView()) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            Listable listable = (Listable)getListView().getItemAtPosition(info.position);
            if (listable instanceof Item && !((Item)listable).isLiveTv() && !((Item)listable).isMusic()) {
                Item item = (Item)listable;
                menu.setHeaderTitle(item.getDialogTitle());
                SparseArray<String> menuItems = mediaActivity.getLongClickMenuItems(item);
                for (int i = 0; i < menuItems.size(); i++) {
                    int id = menuItems.keyAt(i);
                    menu.add(MediaActivity.LIST_FRAGMENT_CONTEXT_MENU_GROUP_ID, id, id, menuItems.get(id));
                }
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getGroupId() == MediaActivity.LIST_FRAGMENT_CONTEXT_MENU_GROUP_ID) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            if (item.getItemId() == MediaActivity.LONG_CLICK_MENU_PLAY) {
                Item it = (Item)getListView().getItemAtPosition(info.position);
                mediaActivity.playItem(it);
                return true;
            } else if (item.getItemId() == MediaActivity.LONG_CLICK_MENU_TRANSCODE) {
                Item it = (Item)getListView().getItemAtPosition(info.position);
                mediaActivity.transcodeItem(it);
                return true;
            } else if (item.getItemId() == MediaActivity.LONG_CLICK_MENU_DOWNLOAD) {
                Item it = (Item)getListView().getItemAtPosition(info.position);
                mediaActivity.downloadItem(it);
                return true;
            } else if (item.getItemId() == MediaActivity.LONG_CLICK_MENU_DELETE) {
                Recording rec = (Recording)getListView().getItemAtPosition(info.position);
                mediaActivity.deleteRecording(rec);
                return true;
            }
        }
        return false;
    }
 }
