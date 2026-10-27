package dev.aicompanion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import java.util.*;
import dev.aicompanion.CompanionExpression;

/** Player geometry with explicit UV islands for the generated atlas. The original PNG is
 * preserved; rectangles avoid transparent margins instead of assuming a 64-pixel skin. */
final class CompanionSkin {
    private final ModelPart root;
    private final Face face;
    private CompanionSkin(ModelPart root, Face face) { this.root = root; this.face = face; }
    ModelPart root() { return root; }
    private static final float SIZE = 1254;
    private record Rect(float x, float y, float right, float bottom) { }
    private static Rect r(float x,float y,float right,float bottom) { return new Rect(x,y,right,bottom); }
    private static final Rect[] HEAD = {r(205,39,338,185),r(339,39,471,185),r(52,190,202,349),r(206,190,363,365),r(367,190,496,349),r(500,190,625,349)};
    private static final Rect[] BODY = {r(432,405,573,501),r(650,405,740,501),r(365,505,428,775),r(432,505,573,775),r(578,505,644,775),r(649,505,819,775)};
    private static final Rect[] ARM = {r(952,405,1051,500),r(952,718,1051,775),r(863,505,947,775),r(952,505,1051,775),r(1056,505,1128,775),r(1133,505,1202,775)};
    private static final Rect[] LEG = {r(136,407,226,500),r(136,713,226,774),r(52,505,131,774),r(136,505,226,774),r(232,505,294,774),r(299,505,361,774)};

    static CompanionSkin bake(ModelPart vanilla) {
        Map<String,ModelPart> children = new HashMap<>();
        Face face = new Face();
        add(children,vanilla,"head",face);
        add(children,vanilla,"body",new Cube(-4,0,-2,8,12,4,BODY));
        add(children,vanilla,"right_arm",new Cube(-3,-2,-2,4,12,4,ARM));
        add(children,vanilla,"left_arm",new Cube(-1,-2,-2,4,12,4,ARM));
        add(children,vanilla,"right_leg",new Cube(-2,0,-2,4,12,4,LEG));
        add(children,vanilla,"left_leg",new Cube(-2,0,-2,4,12,4,LEG));
        for(String name:List.of("hat","ear","cloak","left_sleeve","right_sleeve","left_pants","right_pants","jacket")) add(children,vanilla,name,null);
        return new CompanionSkin(new ModelPart(List.of(),children),face);
    }
    private static void add(Map<String,ModelPart> children,ModelPart vanilla,String name,Cube cube) {
        ModelPart part=new ModelPart(cube==null?List.of():List.of(cube),Map.of());
        var pose=vanilla.getChild(name).storePose(); part.setInitialPose(pose); part.loadPose(pose); children.put(name,part);
    }
    void animate(CompanionExpression expression, float mouth, float age) {
        face.expression = expression;
        face.mouth = mouth;
        float phase = age % 137;
        face.blink = phase < 5 ? (float)Math.sin(phase * Math.PI / 5) : 0;
    }
    private static class Cube extends ModelPart.Cube {
        private final Vector3f[] points;
        private final Rect[] uv;
        // Same winding and face order as vanilla ModelPart.Cube: DOWN, UP, WEST, NORTH, EAST, SOUTH.
        private static final int[][] FACES={{5,4,0,1},{2,3,7,6},{0,4,7,3},{1,0,3,2},{5,1,2,6},{4,5,6,7}};
        private static final Direction[] NORMALS={Direction.DOWN,Direction.UP,Direction.WEST,Direction.NORTH,Direction.EAST,Direction.SOUTH};
        Cube(float x,float y,float z,float w,float h,float d,Rect[] uv) {
            super(0,0,x,y,z,w,h,d,0,0,0,false,64,64,Set.of());
            this.uv=uv;
            points=new Vector3f[]{new Vector3f(x,y,z),new Vector3f(x+w,y,z),new Vector3f(x+w,y+h,z),new Vector3f(x,y+h,z),
                    new Vector3f(x,y,z+d),new Vector3f(x+w,y,z+d),new Vector3f(x+w,y+h,z+d),new Vector3f(x,y+h,z+d)};
        }
        @Override public void compile(PoseStack.Pose pose,VertexConsumer buffer,int light,int overlay,int color) {
            for(int face=0;face<6;face++) {
                if (face == 3 && this instanceof Face front) { front.front(pose,buffer,light,overlay,color); continue; }
                Rect rect=uv[face];
                Vector3f normal=pose.transformNormal(NORMALS[face].step(),new Vector3f());
                float top=face==1?rect.bottom:rect.y, bottom=face==1?rect.y:rect.bottom;
                for(int vertex=0;vertex<4;vertex++) {
                    Vector3f p=new Vector3f(points[FACES[face][vertex]]).div(16);
                    pose.pose().transformPosition(p);
                    float u=(vertex==0||vertex==3?rect.right:rect.x)/SIZE;
                    float v=(vertex<2?top:bottom)/SIZE;
                    buffer.addVertex(p.x,p.y,p.z,color,u,v,overlay,light,normal.x,normal.y,normal.z);
                }
            }
        }
    }

    /** The original face is a small deformable mesh. Lips separate along each expression's own seam. */
    private static final class Face extends Cube {
        private CompanionExpression expression = CompanionExpression.CALM;
        private float mouth, blink;
        private static final float[] COLUMNS = {0,7,16,27,36,48,60,72,78,88,98,110,122,135,144,151,157};
        private static final float[] ROWS = {0,25,43,58,64,72,80,88,108,125,0,163,175};
        Face() { super(-4,-8,-4,8,8,8,HEAD); }

        private float seam(float u) {
            float x = Math.max(-1, Math.min(1, (u - 78) / 48));
            return switch (expression) {
                case SMIRK -> 145 - x * 8;
                case PLEASED -> 146 - 12 * x * x;
                case WORRIED, SURPRISED -> 137 + 9 * x * x;
                case COMBAT -> 141 + 4 * x * x;
                default -> 141;
            };
        }
        private float gap(float u) {
            float width = expression == CompanionExpression.PLEASED || expression == CompanionExpression.SMIRK ? 48 : 31;
            float x = Math.abs(u - 78) / width;
            return mouth * 16 * Math.max(0, 1 - x * x);
        }
        private float y(float u, float v, boolean lower) {
            float lip = seam(u), opening = gap(u);
            float result = v;
            if (lower) result += opening * .8F * Math.max(0, (175 - v) / (175 - lip));
            else result -= opening * .2F * Math.max(0, (v - 125) / (lip - 125));
            // Move existing eyelid pixels over the eyes; skin and eyebrows remain part of the same mesh.
            boolean eye = u >= 16 && u <= 60 || u >= 98 && u <= 144;
            if (eye && v >= 58 && v <= 88) {
                float closing = expression == CompanionExpression.DISPLEASED ? Math.max(.4F,blink) : blink;
                result += closing * .95F * (v < 64 ? (v - 58) * 16 / 6 : Math.max(0, 80 - v));
            }
            float brow = Math.max(0, 1 - Math.min(Math.abs(u - 40),Math.abs(u - 118)) / 38);
            if (expression == CompanionExpression.SURPRISED && v >= 25 && v <= 88) {
                result -= brow * 9 * (float)Math.sin((v - 25) / 63 * Math.PI);
            } else if (expression == CompanionExpression.DISPLEASED && v >= 25 && v <= 58) {
                result += brow * (u < 78 ? (u - 40) : (118 - u)) * .55F * (float)Math.sin((v - 25) / 33 * Math.PI);
            }
            return result;
        }
        void front(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color) {
            Vector3f normal = pose.transformNormal(Direction.NORTH.step(),new Vector3f());
            for (int col = 0; col < COLUMNS.length - 1; col++) {
                float a = COLUMNS[col], b = COLUMNS[col + 1];
                for (int row = 0; row < ROWS.length - 1; row++) {
                    float va = row == 10 ? seam(a) : ROWS[row], vb = row == 10 ? seam(b) : ROWS[row];
                    float wa = row + 1 == 10 ? seam(a) : ROWS[row + 1], wb = row + 1 == 10 ? seam(b) : ROWS[row + 1];
                    boolean lower = row >= 10;
                    vertex(pose,buffer,normal,b,vb,y(b,vb,lower),light,overlay,color);
                    vertex(pose,buffer,normal,a,va,y(a,va,lower),light,overlay,color);
                    vertex(pose,buffer,normal,a,wa,y(a,wa,lower),light,overlay,color);
                    vertex(pose,buffer,normal,b,wb,y(b,wb,lower),light,overlay,color);
                }
                if (mouth > 0) {
                    float va = seam(a), vb = seam(b);
                    // Dark interior uses the same atlas and lighting as the lips, without another texture or draw call.
                    int dark = net.minecraft.util.FastColor.ARGB32.multiply(color, 0xFF362028);
                    vertex(pose,buffer,normal,b,vb,y(b,vb,false),light,overlay,dark);
                    vertex(pose,buffer,normal,a,va,y(a,va,false),light,overlay,dark);
                    vertex(pose,buffer,normal,a,va,y(a,va,true),light,overlay,dark);
                    vertex(pose,buffer,normal,b,vb,y(b,vb,true),light,overlay,dark);
                }
            }
        }
        private void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3f normal, float u, float v, float drawnY,
                            int light, int overlay, int color) {
            Vector3f position = new Vector3f((-4 + u / 157 * 8) / 16,(-8 + drawnY / 175 * 8) / 16,-4 / 16F);
            pose.pose().transformPosition(position);
            buffer.addVertex(position.x,position.y,position.z,color,(206+u)/SIZE,(190+v)/SIZE,overlay,light,normal.x,normal.y,normal.z);
        }
    }
}
