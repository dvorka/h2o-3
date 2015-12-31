package hex.schemas;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import water.AutoBuffer;
import water.H2O;
import water.Job;
import water.api.*;
import water.api.ValidationMessageBase;
import water.util.*;
import java.util.Properties;

public class ModelBuilderSchema<B extends ModelBuilder, S extends ModelBuilderSchema<B,S,P>, P extends ModelParametersSchema> extends RequestSchema<B,S> implements SpecifiesHttpResponseCode {
  // NOTE: currently ModelBuilderSchema has its own JSON serializer.
  // If you add more fields here you MUST add them to writeJSON_impl() below.

  public static class IcedHashMapStringModelBuilderSchema extends IcedSortedHashMap<String, ModelBuilderSchema> {}

  // Input fields
  @API(help="Model builder parameters.")
  public P parameters;

  // Output fields
  @API(help="The algo name for this ModelBuilder.", direction=API.Direction.OUTPUT)
  public String algo;

  @API(help="The pretty algo name for this ModelBuilder (e.g., Generalized Linear Model, rather than GLM).", direction=API.Direction.OUTPUT)
  public String algo_full_name;

  @API(help="Model categories this ModelBuilder can build.", values={ "Unknown", "Binomial", "Multinomial", "Regression", "Clustering", "AutoEncoder", "DimReduction" }, direction = API.Direction.OUTPUT)
  public ModelCategory[] can_build;

  @API(help="Should the builder always be visible, be marked as beta, or only visible if the user starts up with the experimental flag?", values = { "Experimental", "Beta", "AlwaysVisible" }, direction = API.Direction.OUTPUT)
  public ModelBuilder.BuilderVisibility visibility;

  @API(help = "Job Key", direction = API.Direction.OUTPUT)
  public JobV3 job;

  @API(help="Parameter validation messages", direction=API.Direction.OUTPUT)
  public ValidationMessageBase messages[];

  @API(help="Count of parameter validation errors", direction=API.Direction.OUTPUT)
  public int error_count;

  @API(help="HTTP status to return for this build.", json = false)
  public int __http_status; // The handler sets this to 400 if we're building and error_count > 0, else 200.

  public ModelBuilderSchema() {
    this.parameters = createParametersSchema();
  }

  public void setHttpStatus(int status) {
    __http_status = status;
  }

  public int httpStatus() {
    return __http_status;
  }

  /** Factory method to create the model-specific parameters schema. */
  final public P createParametersSchema() {
    // special case, because ModelBuilderSchema is the top of the tree and is parameterized differently
    if (ModelBuilderSchema.class == this.getClass()) {
      return (P)new ModelParametersSchema();
    }

    try {
      Class<? extends ModelParametersSchema> parameters_class = (Class<? extends ModelParametersSchema>) ReflectionUtils.findActualClassParameter(this.getClass(), 2);
      return (P)parameters_class.newInstance();
    }
    catch (Exception e) {
      throw H2O.fail("Caught exception trying to instantiate a builder instance for ModelBuilderSchema: " + this + ": " + e, e);
    }
  }

  public S fillFromParms(Properties parms) {
    this.parameters.fillFromParms(parms);
    return (S)this;
  }

  /** Create the corresponding impl object, as well as its parameters object. */
  @Override final public B createImpl() {
    return ModelBuilder.make(get__meta().getSchema_type(), null, null);
  }

  @Override public B fillImpl(B impl) {
    super.fillImpl(impl);
    parameters.fillImpl(impl._parms);
    impl.init(false); // validate parameters
    return impl;
  }

  // Generic filling from the impl
  @Override public S fillFromImpl(B builder) {
    // DO NOT, because it can already be running: builder.init(false); // check params

    this.algo = builder._parms.algoName().toLowerCase();
    this.algo_full_name = builder._parms.fullName();

    this.can_build = builder.can_build();
    this.visibility = builder.builderVisibility();
    job = (JobV3)Schema.schema(this.getSchemaVersion(), Job.class).fillFromImpl(builder._job);
    if( builder._messages != null ) {
      this.messages = new ValidationMessageBase[builder._messages.length];
      int i = 0;
      for (ModelBuilder.ValidationMessage vm : builder._messages) {
        this.messages[i++] = new ValidationMessageV3().fillFromImpl(vm); // TODO: version // Note: does default field_name mapping
      }
      // default fieldname hacks
      ValidationMessageBase.mapValidationMessageFieldNames(this.messages, new String[]{"_train", "_valid"}, new String[]{"training_frame", "validation_frame"});
    }
    this.error_count = builder.error_count();
    parameters = createParametersSchema();
    parameters.fillFromImpl(builder._parms);
    parameters.model_id = builder.dest() == null ? null : new KeyV3.ModelKeyV3(builder.dest());
    return (S)this;
  }

  // TODO: Drop this writeJSON_impl and use the default one.
  // TODO: Pull out the help text & metadata into the ParameterSchema for the front-end to display.
  @Override
  public AutoBuffer writeJSON_impl( AutoBuffer ab ) {
    ab.put1(','); // the schema and version fields get written before we get called
    ab.putJSON("job", job);
    ab.put1(',');
    ab.putJSONStr("algo", algo);
    ab.put1(',');
    ab.putJSONStr("algo_full_name", algo_full_name);
    ab.put1(',');
    ab.putJSONAEnum("can_build", can_build);
    ab.put1(',');
    ab.putJSONEnum("visibility", visibility);
    ab.put1(',');
    ab.putJSONA("messages", messages);
    ab.put1(',');
    ab.putJSON4("error_count", error_count);
    ab.put1(',');

    // Builds ModelParameterSchemaV2 objects for each field, and then calls writeJSON on the array
    ModelParametersSchema.writeParametersJSON(ab, parameters, createParametersSchema().fillFromImpl((Model.Parameters)parameters.createImpl()));
    return ab;
  }

}
