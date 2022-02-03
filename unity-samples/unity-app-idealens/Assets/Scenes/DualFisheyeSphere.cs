using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using System;

[ExecuteInEditMode]
[RequireComponent(typeof(MeshRenderer))]
[RequireComponent(typeof(MeshFilter))]
public class DualFisheyeSphere : MonoBehaviour
{
    private static readonly double FISH_RAD = 0.883;
    private static readonly double FISH_RAD2 = FISH_RAD * 1.0;
    private static readonly double FISH_CENTER = 0.5;

    private static readonly int U_STEP = 5;
    private static readonly int V_STEP = 5;

    private Mesh mesh;
    private MeshFilter meshFilter;

    // Start is called before the first frame update
    void Start()
    {
        meshFilter = (MeshFilter)GetComponent("MeshFilter");
        mesh = MakeSphere();

        meshFilter.sharedMesh = mesh;
        meshFilter.sharedMesh.name = "DualFisheye";
    }

    void Update()
    {
    }

    private void Cyln2Wold(double a, double e, ref double x, ref double y, ref double z)
    {
        x = Math.Cos(e) * Math.Cos(a);
        y = Math.Cos(e) * Math.Sin(a);
        z = Math.Sin(e);
    }

    private void World2Fish(double x, double y, double z, ref double a, ref double r)
    {
        a = Math.Atan2(y, x);
        var nz = z;
        if (nz < -1.0)
        {
            nz = -1.0;
        }
        else if (z > 1.0)
        {
            nz = 1.0;
        }
        r = Math.Acos(nz) / Math.PI;
    }

    private float GetXPos(int i, int j)
    {
        return (float)(Math.Sin(j * Math.PI / 180.0) * Math.Sin(i * Math.PI / 180.0) * 5.0);
    }

    private float GetYPos(int i, int j)
    {
        return (float)(Math.Cos(j * Math.PI / 180.0) * 5.0);
    }

    private float GetZPos(int i, int j)
    {
        return (float)(Math.Sin(j * Math.PI / 180.0) * Math.Cos(i * Math.PI / 180.0) * 5.0);
    }

    private void GetTexUV(double i, double j, double stepU, double stepV, double[] u, double[] v, bool flag)
    {
        double[] iu = new double[4];
        double[] iv = new double[4];
        double x = 0, y = 0, z = 0, a = 0, r = 0;
        double nx, ny, nz;
        double nx2, ny2, nz2;
        int k;
        double rotY = -90.0 / 180.0 * Math.PI;
        double rotZ = 0.0 / 180.0 * Math.PI;

        iu[0] = ((double)(i) / 180.0 - 1.0) * Math.PI;
        iv[0] = (0.5 - ((double)(j) / 180.0)) * Math.PI;

        iu[1] = ((double)(i + stepU) / 180.0 - 1.0) * Math.PI;
        iv[1] = (0.5 - ((double)(j) / 180.0)) * Math.PI;


        iu[2] = ((double)(i + stepU) / 180.0 - 1.0) * Math.PI;
        iv[2] = (0.5 - ((double)(j + stepV) / 180.0)) * Math.PI;

        iu[3] = ((double)(i) / 180.0 - 1.0) * Math.PI;
        iv[3] = (0.5 - ((double)(j + stepV) / 180.0)) * Math.PI;

        for (k = 0; k < 4; k++)
        {
            Cyln2Wold(iu[k], iv[k], ref x, ref y, ref z);
            nx = Math.Sin(rotY) * z + Math.Cos(rotY) * x;
            ny = y;
            nz = Math.Cos(rotY) * z - Math.Sin(rotY) * x;

            nx2 = Math.Sin(rotZ) * ny + Math.Cos(rotZ) * nx;
            ny2 = Math.Cos(rotZ) * ny - Math.Sin(rotZ) * nx;
            nz2 = nz;

            World2Fish(nx2, ny2, nz2, ref a, ref r);

            if (flag)
            {
                u[k] = FISH_RAD * r * Math.Cos(a) * 0.5 + 0.25;
                v[k] = FISH_RAD2 * r * Math.Sin(a) + FISH_CENTER;
            }
            else
            {

                u[k] = FISH_RAD * (1.0 - r) * Math.Cos(-1.0 * a + Math.PI) * 0.5 + 0.75;
                v[k] = FISH_RAD2 * (1.0 - r) * Math.Sin(-1.0 * a + Math.PI) + FISH_CENTER;
            }
        }
    }

    private Mesh MakeSphere()
    {
        var mesh = new Mesh();

        int i = 0;
        double[] u = new double[4];
        double[] v = new double[4];

        var vertices = new List<Vector3>();
        var uvs = new List<Vector2>();
        var triangles = new List<int>();
        var vertexNum = 0;

        for (int k = 265; k < 455; k += U_STEP)
        {
            i = k % 360;
            for (int j = 0; j < 180; j += V_STEP)
            {
                GetTexUV(i, j, U_STEP, V_STEP, u, v, false);

                uvs.Add(new Vector2((float)u[0], (float)v[0]));
                vertices.Add(new Vector3(GetXPos(i, j), GetYPos(i, j), GetZPos(i, j)));

                uvs.Add(new Vector2((float)u[1], (float)v[1]));
                vertices.Add(new Vector3(
                    GetXPos(i + U_STEP, j), GetYPos(i + U_STEP, j), GetZPos(i + U_STEP, j))
                    );

                uvs.Add(new Vector2((float)u[3], (float)v[3]));
                vertices.Add(new Vector3(
                    GetXPos(i, j + V_STEP), GetYPos(i, j + V_STEP), GetZPos(i, j + V_STEP))
                    );

                uvs.Add(new Vector2((float)u[2], (float)v[2]));
                vertices.Add(new Vector3(
                    GetXPos(i + U_STEP, j + V_STEP), GetYPos(i + U_STEP, j + V_STEP), GetZPos(i + U_STEP, j + V_STEP))
                    );

                triangles.Add(vertexNum * 4 + 0);
                triangles.Add(vertexNum * 4 + 1);
                triangles.Add(vertexNum * 4 + 2);
                triangles.Add(vertexNum * 4 + 3);
                triangles.Add(vertexNum * 4 + 2);
                triangles.Add(vertexNum * 4 + 1);

                vertexNum++;
            }
        }

        for (i = 85; i < 275; i += U_STEP)
        {
            for (int j = 0; j < 180; j += V_STEP)
            {
                GetTexUV(i, j, U_STEP, V_STEP, u, v, true);

                uvs.Add(new Vector2((float)u[0], (float)v[0]));
                vertices.Add(new Vector3(GetXPos(i, j), GetYPos(i, j), GetZPos(i, j)));

                uvs.Add(new Vector2((float)u[1], (float)v[1]));
                vertices.Add(new Vector3(
                    GetXPos(i + U_STEP, j), GetYPos(i + U_STEP, j), GetZPos(i + U_STEP, j))
                    );

                uvs.Add(new Vector2((float)u[3], (float)v[3]));
                vertices.Add(new Vector3(
                    GetXPos(i, j + V_STEP), GetYPos(i, j + V_STEP), GetZPos(i, j + V_STEP))
                    );

                uvs.Add(new Vector2((float)u[2], (float)v[2]));
                vertices.Add(new Vector3(
                    GetXPos(i + U_STEP, j + V_STEP), GetYPos(i + U_STEP, j + V_STEP), GetZPos(i + U_STEP, j + V_STEP))
                    );

                triangles.Add(vertexNum * 4 + 0);
                triangles.Add(vertexNum * 4 + 1);
                triangles.Add(vertexNum * 4 + 2);
                triangles.Add(vertexNum * 4 + 2);
                triangles.Add(vertexNum * 4 + 1);
                triangles.Add(vertexNum * 4 + 3);

                vertexNum++;
            }
        }

        mesh.SetVertices(vertices);
        mesh.SetUVs(0, uvs);
        mesh.SetIndices(triangles.ToArray(), MeshTopology.Triangles, 0);

        mesh.RecalculateBounds();
        mesh.RecalculateNormals();

        return mesh;

    }
}
