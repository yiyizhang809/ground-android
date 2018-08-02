/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gnd.ui.editrecord;

import static com.google.android.gnd.util.Streams.toImmutableList;
import static com.google.android.gnd.vo.PlaceUpdate.Operation;
import static java8.util.stream.StreamSupport.stream;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.navigation.fragment.NavHostFragment;
import butterknife.BindView;
import butterknife.OnClick;
import com.google.android.gnd.MainActivity;
import com.google.android.gnd.R;
import com.google.android.gnd.repository.Resource;
import com.google.android.gnd.ui.common.AbstractFragment;
import com.google.android.gnd.ui.common.EphemeralPopups;
import com.google.android.gnd.ui.common.ProgressDialogs;
import com.google.android.gnd.ui.common.TwoLineToolbar;
import com.google.android.gnd.ui.editrecord.input.Editable;
import com.google.android.gnd.ui.editrecord.input.MultipleChoiceFieldViewHolder;
import com.google.android.gnd.ui.editrecord.input.TextInputViewHolder;
import com.google.android.gnd.vo.Form;
import com.google.android.gnd.vo.Record;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class EditRecordFragment extends AbstractFragment {
  private static final String TAG = EditRecordFragment.class.getSimpleName();
  private static final String NEW_RECORD_ID_ARG_PLACEHOLDER = "NEW_RECORD";

  private ProgressDialog savingProgressDialog;

  private EditRecordViewModel viewModel;

  @BindView(R.id.edit_record_toolbar)
  TwoLineToolbar toolbar;

  @BindView(R.id.form_name)
  TextView formNameView;

  @BindView(R.id.edit_record_progress_bar)
  ProgressBar progressBar;

  @BindView(R.id.edit_record_layout)
  LinearLayout formLayout;

  private List<Editable> fields;

  @Override
  public void onCreate(@android.support.annotation.Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    viewModel = get(EditRecordViewModel.class);
  }

  @Override
  public View onCreateView(
    LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.edit_record_frag, container, false);
  }

  @Override
  public void onViewCreated(
    @NonNull View view, @android.support.annotation.Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    ((MainActivity) getActivity()).setActionBar(toolbar, R.drawable.ic_close);
    savingProgressDialog = ProgressDialogs.modalSpinner(getContext(), R.string.saving);
  }

  @Override
  public void onActivityCreated(@android.support.annotation.Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    viewModel.getRecord().observe(this, this::onRecordChange);
  }

  @Override
  public void onStart() {
    super.onStart();
    Resource<Record> record = Resource.getValue(viewModel.getRecord());
    if (record.isLoaded()) {
      onRecordChange(record);
      return;
    }
    EditRecordFragmentArgs args = EditRecordFragmentArgs.fromBundle(getArguments());
    if (args.getRecordId().equals(NEW_RECORD_ID_ARG_PLACEHOLDER)) {
      viewModel.editNewRecord(args.getProjectId(), args.getPlaceId(), args.getFormId());
    } else {
      viewModel.editExistingRecord(args.getProjectId(), args.getPlaceId(), args.getRecordId());
    }
  }

  private void onRecordChange(Resource<Record> record) {
    switch (record.getStatus()) {
      case LOADED:
        record.ifPresent(this::editRecord);
        break;
      case SAVING:
        savingProgressDialog.show();
        break;
      case SAVED:
        savingProgressDialog.hide();
        EphemeralPopups.showSuccess(getContext(), R.string.saved);
        NavHostFragment.findNavController(this).navigateUp();
        break;
      case NOT_FOUND:
      case ERROR:
        // TODO: Replace w/error view?
        record.getError().ifPresent(t -> Log.e(TAG, "Failed to load/save record", t));
        EphemeralPopups.showError(getContext());
        break;
    }
  }

  private void editRecord(Record record) {
    progressBar.setVisibility(View.GONE);
    toolbar.setTitle(record.getPlace().getTitle());
    toolbar.setSubtitle(record.getPlace().getSubtitle());
    formNameView.setText(record.getForm().getTitle());
    rebuildForm(record);
  }

  // TODO: Move into EditRecordFormViewHolder class.
  private void rebuildForm(Record record) {
    formLayout.removeAllViews();
    fields = new ArrayList<>();
    for (Form.Element element : record.getForm().getElements()) {
      switch (element.getType()) {
        case FIELD:
          Editable editable = addField(element.getField(), record);
          if (editable != null) {
            fields.add(editable);
          }
          break;
        default:
          Log.d(TAG, element.getType() + " elements not yet supported");
      }
    }
    if (fields.isEmpty()) {
      // TODO: Show "empty form" error message.
    }
  }

  @Nullable
  private Editable addField(Form.Field field, Record record) {
    switch (field.getType()) {
      case TEXT:
        // TODO: Refactor these views into ViewHolders and use normal instances of Android view
        // components instead of extending them.
        TextInputViewHolder textInput = TextInputViewHolder.newInstance(
          this,
          viewModel,
          formLayout);
        textInput.init(field, record);
        formLayout.addView(textInput.getView());
        return textInput;
      case MULTIPLE_CHOICE:
        MultipleChoiceFieldViewHolder multipleChoice =
          MultipleChoiceFieldViewHolder.newInstance(formLayout);
        multipleChoice.init(field, record);
        formLayout.addView(multipleChoice.getView());
        return multipleChoice;
      default:
        return null;
    }
  }

  @OnClick(R.id.save_record_btn)
  void onSaveClick() {
    viewModel.saveChanges(
        stream(fields)
          .map(Editable::getUpdate)
          .filter(u -> !u.getOperation().equals(Operation.NO_CHANGE))
          .collect(toImmutableList()));
  }
}
