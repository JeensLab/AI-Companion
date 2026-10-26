package dev.aicompanion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import java.util.*;

/** Player geometry with explicit UV islands for the generated atlas. The original PNG is
 * preserved; rectangles avoid transparent margins instead of assuming a 64-pixel skin. */
final class CompanionSkin {
    private static final float SIZE = 1254;
    private record Rect(float x, float y, float right, float bottom) { }
    private static Rect r(float x,float y,float right,float bottom) { return new Rect(x,y,right,bottom); }
    private static final Rect[] HEAD = {r(205,39,338,185),r(339,39,471,185),r(52,190,202,349),r(206,190,363,365),r(367,190,496,349),r(500,190,625,349)};
    private static final Rect[] BODY = {r(432,405,573,501),r(650,405,740,501),r(365,505,428,775),r(432,505,573,775),r(578,505,644,775),r(649,505,819,775)};
    private static final Rect[] ARM = {r(952,405,1051,500),r(952,718,1051,775),r(863,505,947,775),r(952,505,1051,775),r(1056,505,1128,775),r(1133,505,1202,775)};
    private static final Rect[] LEG = {r(136,407,226,500),r(136,713,226,774),r(52,505,131,774),r(136,505,226,774),r(232,505,294,774),r(299,505,361,774)};

    static ModelPart root(ModelPart vanilla) {
        Map<String,ModelPart> children = new HashMap<>();
        add(children,vanilla,"head",new Cube(-4,-8,-4,8,8,8,HEAD));
        add(children,vanilla,"body",new Cube(-4,0,-2,8,12,4,BODY));
        add(children,vanilla,"right_arm",new Cube(-3,-2,-2,4,12,4,ARM));
        add(children,vanilla,"left_arm",new Cube(-1,-2,-2,4,12,4,ARM));
        add(children,vanilla,"right_leg",new Cube(-2,0,-2,4,12,4,LEG));
        add(children,vanilla,"left_leg",new Cube(-2,0,-2,4,12,4,LEG));
        for(String name:List.of("hat","ear","cloak","left_sleeve","right_sleeve","left_pants","right_pants","jacket")) add(children,vanilla,name,null);
        return new ModelPart(List.of(),children);
    }
    private static void add(Map<String,ModelPart> children,ModelPart vanilla,String name,Cube cube) {
        ModelPart part=new ModelPart(cube==null?List.of():List.of(cube),Map.of());
        var pose=vanilla.getChild(name).storePose(); part.setInitialPose(pose); part.loadPose(pose); children.put(name,part);
    }
    private static final class Cube extends ModelPart.Cube {
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
}
