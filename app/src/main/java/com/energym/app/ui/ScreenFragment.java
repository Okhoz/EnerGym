package com.energym.app.ui;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.controller.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.checkbox.MaterialCheckBox;
import java.util.*;
import java.util.function.*;
/** VISTA MVC compartida: infla filas XML, muestra texto y remite eventos sin persistencia ni decisiones de dominio. */
public abstract class ScreenFragment extends Fragment {
    protected AppState state;protected AppController app;protected LinearLayout rows;private final int layout;
    protected ScreenFragment(int layout){this.layout=layout;}
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle saved){return inflater.inflate(layout,container,false);}
    @Override public void onViewCreated(View view,Bundle saved){super.onViewCreated(view,saved);render();}
    public final void render(){if(getView()==null)return;state=((MainActivity)requireActivity()).state();app=state.app;if(app==null)return;rows=getView().findViewById(R.id.rows);rows.removeAllViews();content();}
    protected abstract void content();
    protected void heading(String eyebrow,String title,String subtitle){((TextView)getView().findViewById(R.id.eyebrow)).setText(eyebrow);((TextView)getView().findViewById(R.id.screen_title)).setText(title);((TextView)getView().findViewById(R.id.screen_subtitle)).setText(subtitle);}
    protected LinearLayout card(String title,String body){View view=getLayoutInflater().inflate(R.layout.row_card,rows,false);((TextView)view.findViewById(R.id.card_title)).setText(title);((TextView)view.findViewById(R.id.card_body)).setText(body);rows.addView(view);return view.findViewById(R.id.card_actions);}
    protected MaterialButton action(LinearLayout target,String label,boolean enabled,Runnable event){MaterialButton b=(MaterialButton)getLayoutInflater().inflate(R.layout.row_action,target,false);b.setText(label);b.setEnabled(enabled);b.setOnClickListener(v->event.run());target.addView(b);return b;}
    protected void action(LinearLayout target,String label,Runnable event){action(target,label,true,event);}
    protected void textField(LinearLayout target,String key,String label,String value,boolean number,boolean integer,Consumer<String> event){
        TextInputLayout field=(TextInputLayout)getLayoutInflater().inflate(R.layout.row_field,target,false);field.setHint(label);EditText edit=field.findViewById(R.id.field_input);edit.setId(View.generateViewId());edit.setTag(key);
        if(number)edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED|(integer?0:android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL));
        edit.setText(value);edit.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){event.accept(s.toString());}public void afterTextChanged(Editable e){}});target.addView(field);
    }
    protected void choice(LinearLayout target,String key,String label,List<String> values,List<String> labels,String value,Consumer<String> event){
        View field=getLayoutInflater().inflate(R.layout.row_choice,target,false);TextView text=field.findViewById(R.id.choice_label);text.setText(label);Spinner spinner=field.findViewById(R.id.choice_input);spinner.setId(View.generateViewId());spinner.setTag(key);text.setLabelFor(spinner.getId());spinner.setContentDescription(label);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);int index=Math.max(0,values.indexOf(value));spinner.setSelection(index,false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){private int last=index;public void onItemSelected(AdapterView<?> parent,View view,int position,long id){if(position!=last){last=position;event.accept(values.get(position));}}public void onNothingSelected(AdapterView<?> parent){}});target.addView(field);
    }
    protected void check(LinearLayout target,AppController.InputField field){MaterialCheckBox box=(MaterialCheckBox)getLayoutInflater().inflate(R.layout.row_check,target,false);box.setId(View.generateViewId());box.setTag(field.key);box.setText(field.label);box.setChecked(Boolean.parseBoolean(field.value));box.setOnCheckedChangeListener((button,value)->app.profileInput(field.key,Boolean.toString(value)));target.addView(box);}
}
