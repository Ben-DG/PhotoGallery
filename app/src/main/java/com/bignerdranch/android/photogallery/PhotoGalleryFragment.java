package com.bignerdranch.android.photogallery;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

import static android.support.v7.widget.RecyclerView.SCROLL_STATE_DRAGGING;
import static android.support.v7.widget.RecyclerView.SCROLL_STATE_IDLE;
import static android.support.v7.widget.RecyclerView.SCROLL_STATE_SETTLING;

public class PhotoGalleryFragment extends Fragment {

    private static final String TAG = "PhotoGalleryFragment";

    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    public static int pageNum = 1;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
        updateItems();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);

        mPhotoRecyclerView = (RecyclerView) v.findViewById(R.id.photo_recycler_view);
        mPhotoRecyclerView.setLayoutManager
                (new GridLayoutManager(getActivity(), 3));
        updateAdapter(); // Called here so every time a new RecyclerView is created it is
        // reconfigured with the appropriate adapter. Also want to call every time the set of model
        // objects changes.
        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (!recyclerView.canScrollVertically(1)) {
                    updateItems();
                }

                if (newState == SCROLL_STATE_IDLE) {
                    int firstVisible = ((GridLayoutManager) recyclerView.getLayoutManager())
                            .findFirstVisibleItemPosition();
                    for (int i = firstVisible; i > firstVisible - 10 && i >= 0; i--) {
                        Picasso.get()
                                .load(mItems.get(i).getUrl())
                                .fetch();
                    }

                    int lastVisible = ((GridLayoutManager) recyclerView.getLayoutManager())
                            .findLastVisibleItemPosition();
                    for (int i = lastVisible; i < lastVisible + 10 && i < mItems.size(); i++) {
                        Picasso.get()
                                .load(mItems.get(i).getUrl())
                                .fetch();
                    }
                }
            }
        });
        mPhotoRecyclerView.getViewTreeObserver()
                .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        float columnWidthInPixels = TypedValue.applyDimension
                                (TypedValue.COMPLEX_UNIT_DIP, 140,
                                        getActivity().getResources().getDisplayMetrics());
                        int width = mPhotoRecyclerView.getWidth();
                        int numCols = Math.round(width / columnWidthInPixels);
                        GridLayoutManager glm = (GridLayoutManager) mPhotoRecyclerView
                                .getLayoutManager();
                        glm.setSpanCount(numCols);
                        mPhotoRecyclerView.getViewTreeObserver()
                                .removeOnGlobalLayoutListener(this);
                    }
                });

        return v;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_photo_gallery, menu);

        final MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(TAG, "QueryTextSubmit: " + query);
                QueryPreferences.setStoredQuery(getActivity(), query);
                pageNum = 1;
                updateItems();
                searchView.clearFocus();
                searchView.onActionViewCollapsed();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d(TAG, "QueryTextChange: " + newText);
                return false;
            }
        });

        searchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String query = QueryPreferences.getStoredQuery(getActivity());
                searchView.setQuery(query, false);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_clear:
                pageNum = 1;
                QueryPreferences.setStoredQuery(getActivity(), null);
                updateItems();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateItems() {
        String query = QueryPreferences.getStoredQuery(getActivity());
        new FetchItemsTask(query).execute();
        pageNum++;
    }

    private void updateAdapter() {
        if (isAdded()) { // Needed because this fragment is retained
            if (pageNum > 2) {
                mPhotoRecyclerView.getAdapter().notifyDataSetChanged();
            } else {
                mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
            }
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;

         public PhotoHolder(View itemView) {
            super(itemView);
            // Expects a view hierarchy that contains an ImageView with this resource ID
            mItemImageView = (ImageView) itemView.findViewById(R.id.item_image_view);
        }

        public void bindGalleryItem(GalleryItem galleryItem) {
            Picasso.get()
                    .load(galleryItem.getUrl())
                    .placeholder(R.drawable.downloading)
                    .into(mItemImageView);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {

        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.list_item_gallery, viewGroup, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder holder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            holder.bindGalleryItem(galleryItem);
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>> {
        private String mQuery;

        public FetchItemsTask(String query) {
            mQuery = query;
        }

        @Override
        protected List<GalleryItem> doInBackground(Void... voids) {
            if (mQuery == null) {
                return new FlickrFetchr().fetchRecentPhotos();
            } else {
                return new FlickrFetchr().searchPhotos(mQuery);
            }
        }

        @Override // This method is run on the main thread so it is safe to update UI
        protected void onPostExecute(List<GalleryItem> items) {
            if (pageNum == 2) {
                mItems = items;
            } else {
                mItems.addAll(items);
            }
            updateAdapter();
        }
    }
}
