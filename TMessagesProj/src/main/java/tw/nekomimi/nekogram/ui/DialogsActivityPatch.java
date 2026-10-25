package tw.nekomimi.nekogram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.FolderNavigationRail;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;

/**
 * Extension helper for DialogsActivity.
 * Manages the vertical folder rail for wide/foldable screens.
 */
public class DialogsActivityPatch {

    /** Min screen width (dp) to activate vertical rail instead of top tabs. */
    public static final int MIN_RAIL_WIDTH_DP = 600;

    private final Context context;
    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;

    private FolderNavigationRail folderRail;
    private final boolean railEnabled;

    public DialogsActivityPatch(Context ctx, int account, Theme.ResourcesProvider rp) {
        this.context           = ctx;
        this.currentAccount    = account;
        this.resourcesProvider = rp;
        this.railEnabled       = AndroidUtilities.displaySize.x >= dp(MIN_RAIL_WIDTH_DP)
                                 || AndroidUtilities.isTablet();
    }

    public boolean isRailEnabled() {
        return railEnabled;
    }

    public void buildRailLayout(
            FrameLayout contentView,
            View[] viewPages,
            View actionBar,
            FilterTabsView filterTabsView,
            FolderNavigationRail.FolderListener listener
    ) {
        if (!railEnabled || contentView == null) {
            return;
        }

        // Hide horizontal tabs — replaced by vertical rail
        if (filterTabsView != null) {
            filterTabsView.setVisibility(View.GONE);
        }

        // Create rail
        folderRail = new FolderNavigationRail(context, currentAccount, resourcesProvider);
        folderRail.setFolderListener(listener);

        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(
                dp(72),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.LEFT | Gravity.TOP
        );
        contentView.addView(folderRail, railParams);

        // Shift viewPages to the right by 72dp
        if (viewPages != null) {
            for (View vp : viewPages) {
                if (vp != null && vp.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) vp.getLayoutParams()).leftMargin = dp(72);
                    vp.requestLayout();
                }
            }
        }

        // Shift actionBar to the right by 72dp
        if (actionBar != null && actionBar.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) actionBar.getLayoutParams()).leftMargin = dp(72);
            actionBar.requestLayout();
        }

        // Populate initial folders
        refreshFolders();
    }

    public void refreshFolders() {
        if (folderRail == null) return;
        ArrayList<MessagesController.DialogFilter> filters =
                MessagesController.getInstance(currentAccount).dialogFilters;
        folderRail.setFolders(filters);
    }

    public void setSelectedFilter(int filterId) {
        if (folderRail != null) {
            folderRail.setSelectedFilter(filterId);
        }
    }

    public FolderNavigationRail getFolderRail() {
        return folderRail;
    }
}
