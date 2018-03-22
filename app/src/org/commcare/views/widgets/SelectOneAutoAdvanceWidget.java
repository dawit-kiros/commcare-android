package org.commcare.views.widgets;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;

import org.commcare.dalvik.R;
import org.commcare.interfaces.AdvanceToNextListener;
import org.commcare.views.media.MediaLayout;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryPrompt;

import java.util.Vector;

/**
 * SelectOneWidgets handles select-one fields using radio buttons. Unlike the classic
 * SelectOneWidget, when a user clicks an option they are then immediately advanced to the next
 * question.
 *
 * @author Jeff Beorse (jeff@beorse.net)
 */
public class SelectOneAutoAdvanceWidget extends QuestionWidget implements OnCheckedChangeListener {

    private final Vector<SelectChoice> mItems;

    private final Vector<RadioButton> buttons;

    private final AdvanceToNextListener listener;

    private final int buttonIdBase;

    public SelectOneAutoAdvanceWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);

        LayoutInflater inflater = LayoutInflater.from(getContext());

        mItems = getSelectChoices();
        buttons = new Vector<>();
        listener = (AdvanceToNextListener)context;

        String s = null;
        if (prompt.getAnswerValue() != null) {
            s = ((Selection)prompt.getAnswerValue().getValue()).getValue();
        }

        //Is this safe enough from collisions?
        buttonIdBase = Math.abs(prompt.getIndex().hashCode());

        if (mItems != null) {
            for (int i = 0; i < mItems.size(); i++) {

                RelativeLayout thisParentLayout =
                        (RelativeLayout)inflater.inflate(R.layout.quick_select_layout, null);

                final LinearLayout questionLayout = (LinearLayout)thisParentLayout.getChildAt(0);
                ImageView rightArrow = (ImageView)thisParentLayout.getChildAt(1);

                final RadioButton r = new RadioButton(getContext());
                r.setOnCheckedChangeListener(this);
                String markdownText = prompt.getSelectItemMarkdownText(mItems.get(i));
                if (markdownText != null) {
                    r.setText(forceMarkdown(markdownText));
                } else {
                    r.setText(prompt.getSelectChoiceText(mItems.get(i)));
                }
                r.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mQuestionFontSize);
                r.setId(i + buttonIdBase);
                r.setEnabled(!prompt.isReadOnly());
                r.setFocusable(!prompt.isReadOnly());

                Drawable image = getResources().getDrawable(R.drawable.icon_auto_advance_arrow);
                rightArrow.setImageDrawable(image);
                rightArrow.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        r.onTouchEvent(event);
                        return false;
                    }
                });

                buttons.add(r);

                if (mItems.get(i).getValue().equals(s)) {
                    r.setChecked(true);
                }

                String audioURI = null;
                audioURI =
                        prompt.getSpecialFormSelectChoiceText(mItems.get(i),
                                FormEntryCaption.TEXT_FORM_AUDIO);

                String imageURI = null;
                imageURI =
                        prompt.getSpecialFormSelectChoiceText(mItems.get(i),
                                FormEntryCaption.TEXT_FORM_IMAGE);

                String videoURI = null;
                videoURI = prompt.getSpecialFormSelectChoiceText(mItems.get(i), "video");

                String bigImageURI = null;
                bigImageURI = prompt.getSpecialFormSelectChoiceText(mItems.get(i), "big-image");

                MediaLayout mediaLayout = MediaLayout.buildAudioImageVisualLayout(getContext(), r, audioURI, imageURI, videoURI, bigImageURI);

                questionLayout.addView(mediaLayout);

                // Last, add the dividing line (except for the last element)
                ImageView divider = new ImageView(getContext());
                divider.setBackgroundResource(android.R.drawable.divider_horizontal_bright);
                if (i != mItems.size() - 1) {
                    mediaLayout.addDivider(divider);
                }

                addView(thisParentLayout);
            }
        }
    }

    @Override
    public void clearAnswer() {
        for (RadioButton button : this.buttons) {
            if (button.isChecked()) {
                button.setChecked(false);
                return;
            }
        }
    }


    @Override
    public IAnswerData getAnswer() {
        int i = getCheckedId();
        if (i == -1) {
            return null;
        } else {
            SelectChoice sc = mItems.elementAt(i - buttonIdBase);
            return new SelectOneData(new Selection(sc));
        }
    }


    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }


    private int getCheckedId() {
        for (RadioButton button : this.buttons) {
            if (button.isChecked()) {
                return button.getId();
            }
        }
        return -1;
    }


    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (!buttonView.isPressed()) {
            return;
        }
        if (!isChecked) {
            // If it got unchecked, we don't care.
            return;
        }

        for (RadioButton button : this.buttons) {
            if (button.isChecked() && !(buttonView == button)) {
                button.setChecked(false);
            }
        }
        widgetEntryChanged();

        listener.advance();
    }


    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        for (RadioButton r : buttons) {
            r.setOnLongClickListener(l);
        }
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        for (RadioButton r : buttons) {
            r.setOnLongClickListener(null);
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        for (RadioButton r : buttons) {
            r.cancelLongPress();
        }
    }

}
