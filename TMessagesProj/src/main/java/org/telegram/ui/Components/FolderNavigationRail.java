package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;

/**
 * Vertical folder navigation rail — left side panel showing chat folders,
 * similar to Telegram Desktop. Replaces horizontal FilterTabsView on
 * wide/foldable screens (width >= 600dp).
 */
public class FolderNavigationRail extends FrameLayout {

    public interface FolderListener {
        void onFolderSelected(int filterId);
    }

    /** Simple data class for one folder entry in the rail. */
    public static class FolderItem {
        public final int filterId;
        public final String title;
        public final int iconRes;
        public int unreadCount;
        public boolean hasMentions;

        public FolderItem(int filterId, String title, int iconRes) {
            this.filterId = filterId;
            this.title    = title;
            this.iconRes  = iconRes;
        }
    }

    // ─── State ───────────────────────────────────────────────────────────────
    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private FolderListener listener;
    private int selectedFilterId = 0;

    private final ArrayList<FolderItem> items = new ArrayList<>();
    private final RecyclerView recyclerView;
    private final FolderAdapter adapter;

    // ─── Constructor ─────────────────────────────────────────────────────────
    public FolderNavigationRail(Context context, int currentAccount, Theme.ResourcesProvider rp) {
        super(context);
        this.currentAccount    = currentAccount;
        this.resourcesProvider = rp;

        setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault, rp));

        recyclerView = new RecyclerView(context);
        recyclerView.setOverScrollMode(OVER_SCROLL_NEVER);
        recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));

        adapter = new FolderAdapter(context);
        recyclerView.setAdapter(adapter);

        addView(recyclerView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ─── Public API ──────────────────────────────────────────────────────────
    public void setFolderListener(FolderListener l) {
        this.listener = l;
    }

    /**
     * Update the folder list. Call whenever MessagesController.dialogFilters changes.
     */
    public void setFolders(ArrayList<MessagesController.DialogFilter> filters) {
        items.clear();

        // "All chats" is always first
        String allChatsLabel = LocaleController.getString("FilterAllChats", R.string.FilterAllChats);
        items.add(new FolderItem(0, allChatsLabel, R.drawable.filter_all));

        if (filters != null) {
            for (MessagesController.DialogFilter f : filters) {
                if (f.isDefault()) continue;
                items.add(new FolderItem(f.id, f.name, pickIcon(f)));
            }
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * Highlight the currently active folder without triggering the listener.
     */
    public void setSelectedFilter(int filterId) {
        if (this.selectedFilterId == filterId) return;
        int oldPos = positionForId(this.selectedFilterId);
        int newPos = positionForId(filterId);
        this.selectedFilterId = filterId;
        if (oldPos >= 0) adapter.notifyItemChanged(oldPos);
        if (newPos >= 0) adapter.notifyItemChanged(newPos);
    }

    /**
     * Update unread badge for a specific filter.
     */
    public void updateUnread(int filterId, int count, boolean mentions) {
        int pos = positionForId(filterId);
        if (pos >= 0) {
            FolderItem item = items.get(pos);
            item.unreadCount = count;
            item.hasMentions = mentions;
            adapter.notifyItemChanged(pos);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private int positionForId(int filterId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).filterId == filterId) return i;
        }
        return -1;
    }

    private int pickIcon(MessagesController.DialogFilter f) {
        // Use emoticon-based icons where possible; fall back to generic
        if (f.emoticon != null && !f.emoticon.isEmpty()) {
            return R.drawable.filter_custom; // emoji rendered separately if needed
        }
        String t = f.name != null ? f.name.toLowerCase() : "";
        if (t.contains("work")  || t.contains("работа"))    return R.drawable.filter_work;
        if (t.contains("unmuted") || t.contains("unmute"))   return R.drawable.filter_unmuted;
        if (t.contains("unread") || t.contains("непрочит"))  return R.drawable.filter_unread;
        if (t.contains("personal") || t.contains("личн"))    return R.drawable.filter_private;
        return R.drawable.filter_custom;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // RecyclerView Adapter
    // ═════════════════════════════════════════════════════════════════════════
    private class FolderAdapter extends RecyclerView.Adapter<FolderViewHolder> {
        private final Context ctx;
        FolderAdapter(Context c) { this.ctx = c; }

        @NonNull
        @Override
        public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new FolderViewHolder(new FolderCellView(ctx));
        }

        @Override
        public void onBindViewHolder(@NonNull FolderViewHolder h, int pos) {
            h.cell.bind(items.get(pos), items.get(pos).filterId == selectedFilterId);
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    private static class FolderViewHolder extends RecyclerView.ViewHolder {
        final FolderCellView cell;
        FolderViewHolder(FolderCellView v) { super(v); this.cell = v; }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Individual folder cell
    // ═════════════════════════════════════════════════════════════════════════
    private class FolderCellView extends FrameLayout {

        private final ImageView iconView;
        private final TextView  labelView;
        private final TextView  badgeView;

        // Selection highlight
        private final Paint  selPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF  selRect  = new RectF();
        private       float  selAlpha = 0f;

        private FolderItem boundItem;

        FolderCellView(Context ctx) {
            super(ctx);
            setClickable(true);

            // Icon
            iconView = new ImageView(ctx);
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LayoutParams iconLp = new LayoutParams(dp(28), dp(28), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            iconLp.topMargin = dp(10);
            addView(iconView, iconLp);

            // Label below icon
            labelView = new TextView(ctx);
            labelView.setTextSize(9);
            labelView.setMaxLines(1);
            labelView.setGravity(Gravity.CENTER_HORIZONTAL);
            LayoutParams labelLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            labelLp.bottomMargin = dp(6);
            addView(labelView, labelLp);

            // Unread badge
            badgeView = new TextView(ctx);
            badgeView.setTextSize(8);
            badgeView.setTextColor(0xFFFFFFFF);
            badgeView.setGravity(Gravity.CENTER);
            badgeView.setPadding(dp(3), dp(1), dp(3), dp(1));
            badgeView.setVisibility(GONE);
            LayoutParams badgeLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
            badgeLp.topMargin  = dp(6);
            badgeLp.rightMargin = dp(6);
            addView(badgeView, badgeLp);

            selPaint.setColor(0x40FFFFFF);
            setMinimumHeight(dp(64));

            setOnClickListener(v -> {
                if (boundItem == null) return;
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (listener != null) listener.onFolderSelected(boundItem.filterId);
            });
        }

        void bind(FolderItem item, boolean selected) {
            this.boundItem = item;

            if (item.iconRes != 0) {
                iconView.setImageResource(item.iconRes);
            }
            labelView.setText(item.title);

            int activeColor   = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider);
            int inactiveColor = Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider);

            iconView.setColorFilter(selected ? activeColor : inactiveColor);
            labelView.setTextColor(selected  ? activeColor : inactiveColor);

            // Badge
            if (item.unreadCount > 0) {
                badgeView.setVisibility(VISIBLE);
                badgeView.setText(item.unreadCount > 99 ? "99+" : String.valueOf(item.unreadCount));
                int badgeColor = item.hasMentions
                        ? Theme.getColor(Theme.key_chats_unreadCounterMuted, resourcesProvider)
                        : Theme.getColor(Theme.key_chats_unreadCounter, resourcesProvider);
                badgeView.setBackground(Theme.createRoundRectDrawable(dp(7), badgeColor));
            } else {
                badgeView.setVisibility(GONE);
            }

            // Animate selection
            float targetAlpha = selected ? 1f : 0f;
            if (Math.abs(selAlpha - targetAlpha) > 0.01f) {
                ValueAnimator va = ValueAnimator.ofFloat(selAlpha, targetAlpha);
                va.setDuration(160);
                va.addUpdateListener(a -> {
                    selAlpha = (float) a.getAnimatedValue();
                    invalidate();
                });
                va.start();
            } else {
                selAlpha = targetAlpha;
                invalidate();
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (selAlpha > 0.01f) {
                selPaint.setAlpha((int) (selAlpha * 40));
                float cx = getWidth() / 2f;
                float cy = dp(10) + dp(14); // icon center
                selRect.set(cx - dp(28), cy - dp(14), cx + dp(28), cy + dp(14));
                canvas.drawRoundRect(selRect, dp(14), dp(14), selPaint);
            }
            super.dispatchDraw(canvas);
        }
    }
}
