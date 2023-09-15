package moe.yushi.yggdrasil_mock.texture;

public enum ModelType {
    STEVE("default"),
    ALEX("slim");

    private final String modelName;

    ModelType(String modelName) {
        this.modelName = modelName;
    }

    public String getModelName() {
        return modelName;
    }
}
