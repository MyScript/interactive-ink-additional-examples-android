/*
 * Copyright (c) MyScript. All rights reserved.
 */

package com.myscript.iink.samples.writetotype;

import android.content.Context;
import android.graphics.RectF;
import android.os.Build;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;

public class CustomViewGroup extends LinearLayout
{
  private static final String PUNCTUATIONS = ".,?!'\"(){}-:;«»„¡¿”•_;·჻՛՜՞՝՚。、~〈〉《》「」〖〗・·…๏๚๛ฯๆ";
  private static final String SPACE = "\u0020";
  private static final String NEW_LINE = "\n";

  /** Interface definition for callbacks invoked when EditText status changed. */
  public interface OnChangedListener
  {
    void onFocusChanged(final EditText editText);
    void onSelectionChanged(final EditText editText, final int selectionStart, final int selectionEnd);
  }

  private OnChangedListener mOnChangedListener = null;

  private EditText mEditText = null;
  private final Map<Integer, Integer> mIndexMap = new HashMap<>();

  /** Event Listener for selection changed and focus changed of EditText. */
  final private View.AccessibilityDelegate mViewDelegate = new View.AccessibilityDelegate() {
    @Override
    public void sendAccessibilityEvent(View host, int eventType)
    {
      super.sendAccessibilityEvent(host, eventType);

      if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED)
      {
        if (mOnChangedListener != null)
        {
          EditText editText = (EditText) host;
          mOnChangedListener.onSelectionChanged(editText, editText.getSelectionStart(), editText.getSelectionEnd());
        }
      }

      if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED)
      {
        if ((mOnChangedListener != null) && (host == mEditText))
        {
          mOnChangedListener.onFocusChanged((EditText) host);
        }
      }
    }
  };

  // --------------------------------------------------------------------------
  // Constructor

  /** Constructor */
  public CustomViewGroup(Context context)
  {
    this(context, null);
  }

  /** Constructor */
  public CustomViewGroup(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  /** Constructor */
  public CustomViewGroup(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
  }

  // --------------------------------------------------------------------------
  // Public methods

  public void setOnChangedListener(OnChangedListener onChangedListener)
  {
    mOnChangedListener = onChangedListener;
  }

  public void initIndexForEditText()
  {
    int childCount = getChildCount();
    int edittextIndex = 0;

    mIndexMap.clear();
    for (int i = 0; i < childCount; i++)
    {
      if (getChildAt(i) instanceof EditText)
      {
        mIndexMap.put(edittextIndex, i);
        edittextIndex++;
      }
    }
  }

  public void resetTextForEditText()
  {
    int edittextCount = mIndexMap.size();
    String [] defaultText = getResources().getStringArray(R.array.default_edittext);

    for (int i = 0; i < edittextCount; i++)
    {
      Integer indexChild = mIndexMap.get(i);
      if (indexChild != null)
      {
        EditText editText = (EditText) getChildAt(indexChild);
        editText.setText(defaultText[i]);
      }
    }
  }

  public EditText findViewByPosition(final float x, final float y)
  {
    EditText editText = null;

    int edittextCount = mIndexMap.size();
    for (int i = 0; i < edittextCount; i++)
    {
      Integer childIndex = mIndexMap.get(i);
      if (childIndex != null)
      {
        EditText childView = (EditText) getChildAt(childIndex);
        float childX = childView.getX();
        float childY = childView.getY();
        float childWidth = childView.getWidth();
        float childHeight = childView.getHeight();
        RectF childViewRect = new RectF(childX, childY, childX + childWidth, childY + childHeight);

        if (childViewRect.contains(x, y))
        {
          editText = childView;
          break;
        }
      }
    }

    return editText;
  }

  public int findCursorByPosition(@NonNull EditText editText, final float x, final float y)
  {
    float relativeX = x - editText.getX();
    float relativeY = y - editText.getY();
    return editText.getOffsetForPosition(relativeX, relativeY);
  }

  public EditText setFocus(final int index)
  {
    Integer childIndex = mIndexMap.get(index);
    if (childIndex != null)
    {
      EditText childView = (EditText) getChildAt(childIndex);
      setFocus(childView);

      return childView;
    }

    return null;
  }

  public void setFocus(EditText editText)
  {
    if (editText != mEditText)
    {
      mEditText = editText;
      editText.setAccessibilityDelegate(mViewDelegate);
      editText.requestFocus();
    }
  }

  public void setSelection(EditText editText, final float x, final float y, final boolean range, final int color)
  {
    if (editText == mEditText)
    {
      int position = findCursorByPosition(editText, x, y);
      String text = editText.getText().toString();

      if (range && text.length() > position)
      {
        String charAtPosition = String.valueOf(text.charAt(position));

        editText.setHighlightColor(color);

        if (charAtPosition.equals(SPACE))
        {
          editText.setSelection(position);
        }
        else if (PUNCTUATIONS.contains(charAtPosition))
        {
          editText.setSelection(position, position + 1);
        }
        else
        {
          for (int i = 0; i < PUNCTUATIONS.length(); i++)
          {
            text = text.replace(PUNCTUATIONS.substring(i, i + 1), SPACE);
          }

          int start = text.lastIndexOf(SPACE, position) + 1;
          int end = ((end = text.indexOf(SPACE, position)) == -1) ? text.length() : end;

          if (isMultiLine(editText))
          {
            int lineStart = text.lastIndexOf(NEW_LINE, position) + 1;
            int lineEnd = ((lineEnd = text.indexOf(NEW_LINE, position)) == -1) ? text.length() : lineEnd;

            start = Math.max(start, lineStart);
            end = Math.min(end, lineEnd);
          }

          editText.setSelection(start, end);
        }
      }
      else
      {
        editText.setSelection(position);
      }
    }
  }

  public void setSelection(EditText editText, @NonNull final RectF selectionRect, final int color)
  {
    if (editText == mEditText)
    {
      editText.setHighlightColor(color);

      int start = findCursorByPosition(editText, selectionRect.left, selectionRect.centerY());
      int end = findCursorByPosition(editText, selectionRect.right, selectionRect.centerY());

      editText.setSelection(start, end);
    }
  }

  public void setText(EditText editText, @NonNull final String label)
  {
    if (editText == mEditText)
    {
      int start = editText.getSelectionStart();
      int end = editText.getSelectionEnd();

      StringBuilder builder = new StringBuilder();

      Editable editable = editText.getEditableText();
      if (start != 0 && !PUNCTUATIONS.contains(label))
      {
        if ((editable.length() == end && !editable.toString().substring(end - 1).equals(SPACE)) ||
            (isMultiLine(editText) && editable.toString().startsWith(NEW_LINE, end)))
        {
          char endChar = editable.charAt(start - 1);
          char startChar = label.charAt(0);

          if (!isCJ(endChar) || !isCJ(startChar))
          {
            builder.append(SPACE);
          }
        }
      }
      builder.append(label);
      editable.replace(start, end, builder.toString());

      editText.setSelection(start + builder.toString().length());
    }
  }

  public void eraseText(EditText editText, @NonNull final RectF eraseRect)
  {
    if (editText == mEditText)
    {
      int start = findCursorByPosition(editText, eraseRect.left, eraseRect.centerY());
      int end = findCursorByPosition(editText, eraseRect.right, eraseRect.centerY());

      editText.setSelection(start);

      Editable editable = editText.getEditableText();
      editable.delete(start, end);
    }
  }

  public void setSpace(EditText editText, final float x, final float y)
  {
    if (editText == mEditText)
    {
      int position = findCursorByPosition(editText, x, y);

      editText.setSelection(position);
      Editable editable = editText.getEditableText();

      int spacePosition = findSpaceNear(editText, SPACE, position);
      if (spacePosition == -1)
      {
        if (findSpaceNear(editText, NEW_LINE, position) == -1 && (editable.length() != position))
        {
          editable.insert(position, SPACE);
        }
        else if (isMultiLine(editText))
        {
          editable.insert(position, NEW_LINE);
        }
      }
      else if (isMultiLine(editText))
      {
        editable.replace(spacePosition, spacePosition + 1, NEW_LINE);
      }
    }
  }

  public void eraseSpace(EditText editText, final float x, final float y)
  {
    if (editText == mEditText)
    {
      int position = findCursorByPosition(editText, x, y);

      editText.setSelection(position);
      Editable editable = editText.getEditableText();

      int spacePosition = findSpaceNear(editText, SPACE, position);
      if (spacePosition != -1)
      {
        editable.delete(spacePosition, spacePosition + 1);
      }
      else if (isMultiLine(editText))
      {
        int newlinePosition = findSpaceNear(editText, NEW_LINE, position);
        if (newlinePosition != -1)
        {
          editable.replace(newlinePosition, newlinePosition + 1, SPACE);
        }
      }
    }
  }

  public void forwardCursor(EditText editText)
  {
    if (editText == mEditText)
    {
      int start = editText.getSelectionStart();
      int end = editText.getSelectionEnd();

      String text = editText.getText().toString();

      if (start != end)
      {
        editText.setSelection(end);
      }
      else if (end < text.length())
      {
        editText.setSelection(end + 1);
      }
    }
  }

  public void backwardDelete(EditText editText)
  {
    if (editText == mEditText)
    {
      int start = editText.getSelectionStart();
      int end = editText.getSelectionEnd();

      Editable editable = editText.getEditableText();

      if (start != end)
      {
        editable.delete(start, end);
        editText.setSelection(start);
      }
      else if (start > 0)
      {
        editText.setSelection(start);
        editable.delete(start - 1, start);
      }
    }
  }

  public boolean isMultiLine(EditText editText)
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    {
      return !editText.isSingleLine();
    }
    else
    {
      return ((editText.getMinLines() > 1) || (editText.getMaxLines() > 1));
    }
  }

  private int findSpaceNear(@NonNull EditText editText, final String target, final int position)
  {
    int newPosition = -1;

    String text = editText.getText().toString();
    if ((0 < position) && (position < text.length()))
    {
      if (String.valueOf(text.charAt(position)).equals(target))
      {
        newPosition = position;
      }
      else if (String.valueOf(text.charAt(position - 1)).equals(target))
      {
        newPosition = (position - 1);
      }
    }

    return newPosition;
  }

  private boolean isCJ(final char c)
  {
    return Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT                   // 2E80..2EFF
        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.KANGXI_RADICALS                           // 2F00..2FDF
        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HIRAGANA                                  // 3040..309F
        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.KATAKANA                                  // 30A0..30FF
        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_STROKES                               // 31C0..31EF
        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A        // 3400..4DBF
        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS                    // 4E00..9FFF
        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS              // F900..FAFF
        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS             // FF00..FFEF
        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.KANA_SUPPLEMENT                           // 1B000..1B0FF
        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B        // 20000..2A6DF
        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C        // 2A700..2B73F
        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D        // 2B740..2B81F
        || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT;  // 2F800..2FA1F
  }
}
