import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Writable;

public class DependencyPathMap extends MapWritable {
    @Override
    public String toString(){
        String out = "{";
        for(Writable key : this.keySet()){
            out += key.toString() + ":" + this.get(key).toString() + ",";
        }
        if(!out.equals("{")){
            out = out.substring(0, out.length() - 1);
        }
        out = out + "}";
        return out;
    }
}
