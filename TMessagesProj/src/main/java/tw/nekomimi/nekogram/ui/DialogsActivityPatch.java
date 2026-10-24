package tw.nekomimi.nekogram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.FolderNavigationRail;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;

/**
 * Extension helper for DialogsActivity.
 * Manages the vertical folder rail for wide/foldable screens.
 *
 * Usage in DialogsActivity.createView():
 *   patch = new DialogsActivityPatch(this, currentAccount, resourcesProvider);
 *   if (patch.isRailEnabled()) {
 *       patch.buildRailLayout(contentView, viewPages, filterTabsView);
 *   }
 */
public class DialogsActivityPatch {

    /** Min screen width (dp) to activate vertical rail instead of top tabs. */
    private static final int MIN_RAIL_WIDTH_DP = 600;

    private final Context context;
    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;

    private FolderNavigationRail folderRail;
    private final boolean railEnabled;

    public DialogsActivityPatch(Context ctx, int account, Theme.ResourcesProvider rp) {
        this.context           = ctx;
        this.currentAccount    = account;
        this.resourcesProvider = rp;
        this.railEnabled       = AndroidUtilities.displaySize.x
                                 >= dp(MIN_RAIL_WIDTH_DP)
                                 || AndroidUtilities.isTablet();
    }

    /** Returns true if vertical rail should be used (wide/foldable screen). */
    public boolean isRailEnabled() {
        return railEnabled;
    }

    /**
     * Rebuilds the dialog screen layout to include the vertical folder rail.
     *
     * @param contentView    Root SizeNotifierFrameLayout of DialogsActivity
     * @param viewPages      Array of ViewPage (chat list pages)
     * @param filterTabsView Existing horizontal FilterTabsView (will be hidden)
     * @param listener       Callback when user taps a folder
     */
    public void buildRailLayout(
            FrameLayout contentView,
            View[] viewPages,
            FilterTabsView filterTabsView,
            FolderNavigationRail.FolderListener listener
    ) {
        // Hide horizontal tabs — we replace them with the vertical rail
        if (filterTabsView != null) {
            filterTabsView.setVisibility(View.GONE);
        }

        // Horizontal container: [72dp rail] + [rest of screen]
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        // Build the rail
        folderRail = new FolderNavigationRail(context, currentAccount, resourcesProvider);
        folderRail.setFolderListener(listener);
        row.addView(folderRail, new LinearLayout.LayoutParams(dp(72),
                LinearLayout.LayoutParams.MATCH_PARENT));

        // Wrap all viewPages into a FrameLayout (right side)
        FrameLayout listContainer = new FrameLayout(context);
        for (View vp : viewPages) {
            if (vp.getParent() != null) {
                ((android.view.ViewGroup) vp.getParent()).removeView(vp);
            }
            listContainer.addView(vp, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        row.addView(listContainer, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        contentView.addView(row, 0, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Initial folder list
        refreshFolders();
    }

    /** Sync folder list from MessagesController. Call on NOTIFICATION_ID_DIALOG_FILTERS_UPDATED. */
    public void refreshFolders() {
        if (folderRail == null) return;
        ArrayList<TLRPC.TL_dialogFilter> filters =
                MessagesController.getInstance(currentAccount).dialogFilters;
        folderRail.setFolders(filters);
    }

    /** Update selected folder highlight. */
    public void setSelectedFilter(int filterId) {
        if (folderRail != null) folderRail.setSelectedFilter(filterId);
    }

    public FolderNavigationRail getFolderRail() {
        return folderRail;
    }
}
